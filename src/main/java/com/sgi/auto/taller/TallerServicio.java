package com.sgi.auto.taller;

import com.sgi.auto.clientes.ClienteRepositorio;
import com.sgi.auto.compartido.GeneradorCodigoSeguro;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.MovimientoStock;
import com.sgi.auto.inventario.MovimientoStockRepositorio;
import com.sgi.auto.inventario.Producto;
import com.sgi.auto.inventario.ProductoRepositorio;
import com.sgi.auto.inventario.TipoMovimientoStock;
import com.sgi.auto.taller.dto.*;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sgi.auto.usuarios.RolUsuario;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TallerServicio {

    private final OrdenDeTrabajoRepositorio otRepositorio;
    private final ProductoRepositorio productoRepositorio;
    private final MovimientoStockRepositorio movimientoStockRepositorio;
    private final ClienteRepositorio clienteRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RepuestoOTRepositorio repuestoOTRepositorio;
    private final ServicioOTRepositorio servicioOTRepositorio;

    private static final int LONGITUD_CODIGO_SEGURO = 8;

    @Transactional
    public OTRespuestaDTO crear(OTCrearDTO solicitud) {
        OrdenDeTrabajo ot = new OrdenDeTrabajo();
        ot.setCodigoSeguro(generarCodigoSeguroUnico());

        // Datos del cliente (denormalizados)
        ot.setNombreCliente(solicitud.nombreCliente());
        ot.setCelularCliente(solicitud.celularCliente());

        if (solicitud.clienteId() != null) {
            clienteRepositorio.findById(solicitud.clienteId())
                    .ifPresent(ot::setCliente);
        }

        // Datos del vehículo
        ot.setPlaca(solicitud.placa().toUpperCase());
        ot.setMarcaVehiculo(solicitud.marcaVehiculo());
        ot.setModeloVehiculo(solicitud.modeloVehiculo());
        ot.setAnioVehiculo(solicitud.anioVehiculo());
        ot.setColorVehiculo(solicitud.colorVehiculo());
        ot.setKilometraje(solicitud.kilometraje());
        ot.setDescripcionProblema(solicitud.descripcionProblema());
        ot.setEstado(EstadoOT.RECIBIDO);
        ot.setDescuentoCop(
                solicitud.descuentoCop() != null
                        ? solicitud.descuentoCop()
                        : BigDecimal.ZERO);
        ot.setFechaPrometidaEntrega(solicitud.fechaPrometidaEntrega());

        // Asignar mecánico
        if (solicitud.mecanicoId() != null) {
            usuarioRepositorio.findById(solicitud.mecanicoId())
                    .ifPresent(ot::setMecanico);
        } else {
            // Si el usuario actual es mecánico, se auto-asigna
            String nombreUsuario = SecurityContextHolder.getContext()
                    .getAuthentication().getName();
            usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                    .ifPresent(usuario -> {
                        if (usuario.getRol() == RolUsuario.MECANICO) {
                            ot.setMecanico(usuario);
                        }
                    });
        }

        OrdenDeTrabajo guardada = otRepositorio.save(ot);
        log.info("OT creada: id={}, numeroOt={}, placa={}",
                guardada.getId(), guardada.getNumeroOt(), guardada.getPlaca());
        // Recargamos para traer numero_ot, que lo genera el trigger de la BD
        // y no viaja en el INSERT que acabamos de hacer.
        return aDTO(buscarOLanzar(guardada.getId()));
    }

    @Transactional(readOnly = true)
    public OTRespuestaDTO obtenerPorId(Long id) {
        return aDTO(buscarOLanzar(id));
    }

    // Búsqueda por el código escaneado del código de barras de la planilla.
    @Transactional(readOnly = true)
    public OTRespuestaDTO obtenerPorCodigoSeguro(String codigoSeguro) {
        OrdenDeTrabajo ot = otRepositorio.findByCodigoSeguro(codigoSeguro)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró ninguna OT con ese código"));
        return aDTO(ot);
    }

    @Transactional(readOnly = true)
    public Page<OTRespuestaDTO> listarActivas(Pageable pageable) {
        return otRepositorio.listarActivas(pageable).map(this::aDTO);
    }

    @Transactional(readOnly = true)
    public Page<OTRespuestaDTO> listarTodas(Pageable pageable) {
        return otRepositorio.listarTodas(pageable).map(this::aDTO);
    }

    // Historial por placa
    @Transactional(readOnly = true)
    public List<OTRespuestaDTO> buscarPorPlaca(String placa) {
        return otRepositorio.buscarPorPlaca(placa.toUpperCase())
                .stream().map(this::aDTO).toList();
    }

    // OTs del mecánico
    @Transactional(readOnly = true)
    public List<OTRespuestaDTO> otActivasPorMecanico(Long mecanicoId) {
        return otRepositorio.otActivasPorMecanico(mecanicoId)
                .stream().map(this::aDTO).toList();
    }

    // ── Servicios de la OT ───────────────────────────

    @Transactional
    public OTRespuestaDTO agregarServicio(Long otId, ServicioOTDTO solicitud) {
        OrdenDeTrabajo ot = buscarOLanzar(otId);
        validarOTModificable(ot);

        ServicioOT servicio = ServicioOT.builder()
                .ordenDeTrabajo(ot)
                .descripcion(solicitud.descripcion())
                .precioUnitarioCop(solicitud.precioUnitarioCop())
                .cantidad(solicitud.cantidad())
                .subtotalCop(solicitud.precioUnitarioCop()
                        .multiply(BigDecimal.valueOf(solicitud.cantidad())))
                .build();

        ServicioOT servicioGuardado = servicioOTRepositorio.save(servicio);
        ot.getServicios().add(servicioGuardado);
        ot.recalcularTotales();
        otRepositorio.save(ot);
        return aDTO(buscarOLanzar(otId));
    }

    @Transactional
    public OTRespuestaDTO eliminarServicio(Long otId, Long servicioId) {
        OrdenDeTrabajo ot = buscarOLanzar(otId);
        validarOTModificable(ot);

        ot.getServicios().removeIf(s -> s.getId().equals(servicioId));
        ot.recalcularTotales();

        return aDTO(otRepositorio.save(ot));
    }

    // ── Repuestos de la OT ───────────────────

    // Agrega un repuesto a la OT y descuenta el stock del inventario.

    @Transactional
    public OTRespuestaDTO agregarRepuesto(Long otId, RepuestoOTDTO solicitud) {
        OrdenDeTrabajo ot = buscarOLanzar(otId);
        validarOTModificable(ot);

        Producto producto = productoRepositorio.findById(solicitud.productoId())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el producto con id: " + solicitud.productoId()));

        // Verificar y descontar stock
        if (producto.getStockActual() < solicitud.cantidad()) {
            throw new ReglaNegocioExcepcion(
                    "Stock insuficiente para '" + producto.getNombre()
                            + "'. Stock actual: " + producto.getStockActual()
                            + ", solicitado: " + solicitud.cantidad());
        }

        int stockAntes = producto.getStockActual();
        producto.setStockActual(stockAntes - solicitud.cantidad());
        productoRepositorio.save(producto);

        // Registrar movimiento en Kardex
        movimientoStockRepositorio.save(MovimientoStock.builder()
                .producto(producto)
                .tipoMovimiento(TipoMovimientoStock.USO_TALLER)
                .cantidad(solicitud.cantidad())
                .stockAntes(stockAntes)
                .stockDespues(producto.getStockActual())
                .costoUnitarioCop(solicitud.precioUnitarioCop())
                .notas("Uso en OT #" + otId)
                .build());

        RepuestoOT repuesto = RepuestoOT.builder()
                .ordenDeTrabajo(ot)
                .producto(producto)
                .nombreRepuestoSnapshot(producto.getNombre())
                .cantidad(solicitud.cantidad())
                .precioUnitarioCop(solicitud.precioUnitarioCop())
                .subtotalCop(solicitud.precioUnitarioCop()
                        .multiply(BigDecimal.valueOf(solicitud.cantidad())))
                .stockDescontado(true)
                .build();

        RepuestoOT repuestoGuardado = repuestoOTRepositorio.save(repuesto);
        ot.getRepuestos().add(repuestoGuardado);
        ot.recalcularTotales();
        otRepositorio.save(ot);

        log.info("Repuesto agregado a OT #{}: producto={}, cantidad={}",
                otId, producto.getNombre(), solicitud.cantidad());

        return aDTO(buscarOLanzar(otId));
    }

    @Transactional
    public OTRespuestaDTO eliminarRepuesto(Long otId, Long repuestoId) {
        OrdenDeTrabajo ot = buscarOLanzar(otId);
        validarOTModificable(ot);

        ot.getRepuestos().stream()
                .filter(r -> r.getId().equals(repuestoId))
                .findFirst()
                .ifPresent(repuesto -> {
                    // Devolver stock si ya fue descontado
                    if (repuesto.isStockDescontado() && repuesto.getProducto() != null) {
                        Producto producto = repuesto.getProducto();
                        int stockAntes = producto.getStockActual();
                        producto.setStockActual(stockAntes + repuesto.getCantidad());
                        productoRepositorio.save(producto);

                        movimientoStockRepositorio.save(MovimientoStock.builder()
                                .producto(producto)
                                .tipoMovimiento(TipoMovimientoStock.DEVOLUCION_ENTRADA)
                                .cantidad(repuesto.getCantidad())
                                .stockAntes(stockAntes)
                                .stockDespues(producto.getStockActual())
                                .notas("Eliminación de repuesto de OT #" + otId)
                                .build());
                    }
                });

        ot.getRepuestos().removeIf(r -> r.getId().equals(repuestoId));
        ot.recalcularTotales();
        return aDTO(otRepositorio.save(ot));
    }

    // ── Estado de la OT ──────────────────────────────

    @Transactional
    public OTRespuestaDTO cambiarEstado(Long otId, CambioEstadoDTO solicitud) {
        OrdenDeTrabajo ot = buscarOLanzar(otId);

        EstadoOT estadoAnterior = ot.getEstado();
        ot.setEstado(solicitud.nuevoEstado());

        if (solicitud.observaciones() != null) {
            ot.setObservacionesMecanico(solicitud.observaciones());
        }

        // Hora de salida automática al entregar
        if (solicitud.nuevoEstado() == EstadoOT.ENTREGADO) {
            ot.setFechaEntregaReal(OffsetDateTime.now());
        }

        OrdenDeTrabajo actualizada = otRepositorio.save(ot);

        log.info("OT #{} cambió estado: {} → {}",
                otId, estadoAnterior, solicitud.nuevoEstado());

        return aDTO(actualizada);
    }

    // ── Helpers privados ──────────────────────────────────────

    private OrdenDeTrabajo buscarOLanzar(Long id) {
        return otRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró la OT con id: " + id));
    }

    private void validarOTModificable(OrdenDeTrabajo ot) {
        if (ot.getEstado() == EstadoOT.ENTREGADO
                || ot.getEstado() == EstadoOT.CANCELADO) {
            throw new ReglaNegocioExcepcion(
                    "No se puede modificar una OT en estado: " + ot.getEstado());
        }
    }

    private String generarCodigoSeguroUnico() {
        String codigo;
        int intentos = 0;
        do {
            codigo = "OT-" + GeneradorCodigoSeguro.generar(LONGITUD_CODIGO_SEGURO);
            if (++intentos > 5) {
                throw new IllegalStateException(
                        "No se pudo generar un código seguro único para la OT");
            }
        } while (otRepositorio.existsByCodigoSeguro(codigo));
        return codigo;
    }

    private OTRespuestaDTO aDTO(OrdenDeTrabajo ot) {
        List<OTRespuestaDTO.ServicioRespuestaDTO> serviciosDTO = ot.getServicios()
                .stream()
                .map(s -> new OTRespuestaDTO.ServicioRespuestaDTO(
                        s.getId(), s.getDescripcion(), s.getCantidad(),
                        s.getPrecioUnitarioCop(), s.getSubtotalCop()))
                .toList();

        List<OTRespuestaDTO.RepuestoRespuestaDTO> repuestosDTO = ot.getRepuestos()
                .stream()
                .map(r -> new OTRespuestaDTO.RepuestoRespuestaDTO(
                        r.getId(), r.getNombreRepuestoSnapshot(), r.getCantidad(),
                        r.getPrecioUnitarioCop(), r.getSubtotalCop(),
                        r.isStockDescontado()))
                .toList();

        return new OTRespuestaDTO(
                ot.getId(),
                ot.getNumeroOt(),
                ot.getCodigoSeguro(),
                ot.getNombreCliente(),
                ot.getCelularCliente(),
                ot.getPlaca(),
                ot.getMarcaVehiculo(),
                ot.getModeloVehiculo(),
                ot.getAnioVehiculo(),
                ot.getColorVehiculo(),
                ot.getKilometraje(),
                ot.getDescripcionProblema(),
                ot.getObservacionesMecanico(),
                ot.getEstado(),
                ot.getMecanico() != null
                        ? ot.getMecanico().getNombreCompleto() : null,
                ot.getMetodoPago(),
                ot.getTotalServiciosCop(),
                ot.getTotalRepuestosCop(),
                ot.getDescuentoCop(),
                ot.getGranTotalCop(),
                serviciosDTO,
                repuestosDTO,
                ot.getFechaPrometidaEntrega(),
                ot.getFechaEntregaReal(),
                ot.getCreadoEn());
    }
}