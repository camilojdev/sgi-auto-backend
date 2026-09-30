package com.sgi.auto.caja;

import com.sgi.auto.caja.dto.*;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.usuarios.Usuario;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CajaServicio {

    private final SesionCajaRepositorio sesionCajaRepositorio;
    private final MovimientoCajaRepositorio movimientoCajaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ApplicationEventPublisher eventPublisher;

    // ── Sesión de Caja ────────────────────────────────────────

    // Abre una nueva sesión de caja. Si la solicitud trae cajeraId, se abre
    // para ese usuario (caso del dueño abriendo la caja de una cajera);
    // si no, se abre para quien está autenticado (autoservicio).
    @Transactional
    public SesionCajaRespuestaDTO abrirSesion(AperturaCajaDTO solicitud) {
        boolean abriendoParaOtro = solicitud.cajeraId() != null;

        Usuario cajera = abriendoParaOtro
                ? usuarioRepositorio.findById(solicitud.cajeraId())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el usuario con id: " + solicitud.cajeraId()))
                : obtenerUsuarioActual();

        // Solo puede haber una sesión abierta a la vez POR CAJERA
        sesionCajaRepositorio.buscarSesionAbiertaPorCajera(cajera.getId()).ifPresent(s -> {
            throw new ReglaNegocioExcepcion(
                    abriendoParaOtro
                            ? cajera.getNombreCompleto() + " ya tiene una sesión de caja abierta."
                            : "Ya tienes una sesión de caja abierta. Ciérrela antes de abrir una nueva.");
        });

        SesionCaja sesion = SesionCaja.builder()
                .cajera(cajera)
                .saldoInicialCop(solicitud.saldoInicialCop())
                .build();

        SesionCaja guardada = sesionCajaRepositorio.save(sesion);

        registrarMovimiento(guardada, TipoMovimientoCaja.APERTURA,
                solicitud.saldoInicialCop(), "Saldo inicial de apertura", null);

        log.info("Sesión de caja abierta: id={}, cajera={}, saldoInicial={}",
                guardada.getId(), cajera.getNombreCompleto(),
                solicitud.saldoInicialCop());

        return aDTO(guardada);
    }

    @Transactional
    public SesionCajaRespuestaDTO cerrarSesion(CierreCajaDTO solicitud) {

        // El dueño puede cerrar la sesión de cualquier cajera
        SesionCaja sesion = sesionCajaRepositorio.findById(solicitud.sesionId())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró la sesión de caja con id: " + solicitud.sesionId()));

        // Verificar que realmente esté abierta
        if (!sesion.isEstaAbierta()) {
            throw new ReglaNegocioExcepcion(
                    "La sesión de caja de " + sesion.getCajera().getNombreCompleto()
                            + " ya está cerrada.");
        }

        // Saldo esperado = inicial + ventas + abonos - gastos
        BigDecimal saldoEsperado = sesion.getSaldoInicialCop()
                .add(sesion.getTotalVentasCop())
                .add(sesion.getTotalAbonosCreditoCop())
                .subtract(sesion.getTotalGastosCop());

        BigDecimal diferencia = solicitud.saldoFinalContadoCop()
                .subtract(saldoEsperado);

        sesion.setSaldoFinalCop(solicitud.saldoFinalContadoCop());
        sesion.setDiferenciaCop(diferencia);
        sesion.setNotasCierre(solicitud.notas());
        sesion.setCerradaEn(OffsetDateTime.now());
        sesion.setEstaAbierta(false);

        SesionCaja cerrada = sesionCajaRepositorio.save(sesion);

        log.info(
                "Sesión de caja cerrada: id={}, cajera={}, cerradaPor={}, diferencia={}",
                cerrada.getId(),
                cerrada.getCajera().getNombreCompleto(),
                obtenerUsuarioActual().getNombreCompleto(),
                diferencia
        );

        eventPublisher.publishEvent(
                new CajaCerradaEvento(cerrada.getId())
        );

        return aDTO(cerrada);
    }

    // Obtiene la sesión abierta del usuario actual.
    @Transactional(readOnly = true)
    public SesionCajaRespuestaDTO obtenerSesionActual() {
        Usuario cajera = obtenerUsuarioActual();
        return sesionCajaRepositorio.buscarSesionAbiertaPorCajera(cajera.getId())
                .map(this::aDTO)
                .orElseThrow(() -> new ReglaNegocioExcepcion(
                        "No tienes ninguna sesión de caja abierta"));
    }

    // Lista todas las sesiones actualmente abiertas (vista del dueño).
    @Transactional(readOnly = true)
    public List<SesionCajaRespuestaDTO> listarSesionesAbiertas() {
        return sesionCajaRepositorio.listarSesionesAbiertas().stream()
                .map(this::aDTO)
                .toList();
    }

    // Historial de cierres filtrado por cajera y/o rango de fechas.
    @Transactional(readOnly = true)
    public Page<SesionCajaRespuestaDTO> listarHistorialFiltrado(
            Long cajeraId, LocalDate desde, LocalDate hasta, Pageable pageable) {

        String desdeStr = desde != null
                ? desde.atStartOfDay().atOffset(ZoneOffset.of("-05:00")).toString()
                : null;
        String hastaStr = hasta != null
                ? hasta.plusDays(1).atStartOfDay().atOffset(ZoneOffset.of("-05:00")).toString()
                : null;

        return sesionCajaRepositorio
                .listarHistorialFiltrado(cajeraId, desdeStr, hastaStr, pageable)
                .map(this::aDTO);
    }

    // Historial de sesiones.
    @Transactional(readOnly = true)
    public Page<SesionCajaRespuestaDTO> listarHistorial(Pageable pageable) {
        return sesionCajaRepositorio.listarTodas(pageable).map(this::aDTO);
    }

    @Transactional(readOnly = true)
    public SesionCajaRespuestaDTO obtenerPorId(Long id) {
        return aDTO(buscarSesionOLanzar(id));
    }

    // ── Movimientos de Caja ───────────────────────────────────

    // Registra un gasto operativo en la sesión abierta del usuario actual.
    @Transactional
    public void registrarGasto(GastoDTO solicitud) {
        Usuario cajera = obtenerUsuarioActual();

        SesionCaja sesion = sesionCajaRepositorio.buscarSesionAbiertaPorCajera(cajera.getId())
                .orElseThrow(() -> new ReglaNegocioExcepcion(
                        "No tienes ninguna sesión de caja abierta"));

        registrarMovimiento(sesion, TipoMovimientoCaja.GASTO,
                solicitud.montoCop(), solicitud.descripcion(), null);

        sesion.setTotalGastosCop(
                sesion.getTotalGastosCop().add(solicitud.montoCop()));
        sesionCajaRepositorio.save(sesion);

        log.info("Gasto registrado en caja: monto={}, descripcion={}",
                solicitud.montoCop(), solicitud.descripcion());
    }

    // Registra un egreso autorizado por el dueño, sobre su propia sesión abierta.
    @Transactional
    public void registrarEgresoDueno(GastoDTO solicitud) {
        Usuario dueno = obtenerUsuarioActual();

        SesionCaja sesion = sesionCajaRepositorio.buscarSesionAbiertaPorCajera(dueno.getId())
                .orElseThrow(() -> new ReglaNegocioExcepcion(
                        "No tienes ninguna sesión de caja abierta"));

        registrarMovimiento(sesion, TipoMovimientoCaja.EGRESO_DUENO,
                solicitud.montoCop(),
                "Egreso dueño: " + solicitud.descripcion(), null);

        sesion.setTotalGastosCop(
                sesion.getTotalGastosCop().add(solicitud.montoCop()));
        sesionCajaRepositorio.save(sesion);
    }

    // Registra automáticamente el ingreso de una venta en la sesión
    // abierta del cajero que la realizó.
    @Transactional
    public void registrarIngresoPorVenta(Long cajeraId, BigDecimal monto, Long ventaId,
                                         BigDecimal montoEfectivo, BigDecimal montoTransferencia, BigDecimal montoCredito) {
        sesionCajaRepositorio.buscarSesionAbiertaPorCajera(cajeraId).ifPresent(sesion -> {
            registrarMovimiento(sesion, TipoMovimientoCaja.VENTA,
                    monto, "Venta POS #" + ventaId, ventaId);
            sesion.setTotalVentasCop(sesion.getTotalVentasCop().add(monto));
            sesion.setTotalEfectivoCop(sesion.getTotalEfectivoCop().add(montoEfectivo));
            sesion.setTotalTransferenciaCop(sesion.getTotalTransferenciaCop().add(montoTransferencia));
            sesion.setTotalCreditoCop(sesion.getTotalCreditoCop().add(montoCredito));
            sesionCajaRepositorio.save(sesion);
        });
    }

    // ── Helpers privados ──────────────────────────────────────

    private void registrarMovimiento(
            SesionCaja sesion,
            TipoMovimientoCaja tipo,
            BigDecimal monto,
            String descripcion,
            Long ventaId) {

        movimientoCajaRepositorio.save(MovimientoCaja.builder()
                .sesion(sesion)
                .tipo(tipo)
                .montoCop(monto)
                .descripcion(descripcion)
                .ventaId(ventaId)
                .build());
    }

    private Usuario obtenerUsuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el usuario autenticado"));
    }

    private SesionCaja buscarSesionOLanzar(Long id) {
        return sesionCajaRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró la sesión de caja con id: " + id));
    }

    private SesionCajaRespuestaDTO aDTO(SesionCaja sesion) {
        List<MovimientoCaja> movimientos =
                movimientoCajaRepositorio.listarPorSesion(sesion.getId());

        BigDecimal saldoEsperado = sesion.getSaldoInicialCop()
                .add(sesion.getTotalVentasCop())
                .add(sesion.getTotalAbonosCreditoCop())
                .subtract(sesion.getTotalGastosCop());

        List<SesionCajaRespuestaDTO.MovimientoRespuestaDTO> movimientosDTO =
                movimientos.stream()
                        .map(m -> new SesionCajaRespuestaDTO.MovimientoRespuestaDTO(
                                m.getId(), m.getTipo(),
                                m.getMontoCop(), m.getDescripcion(),
                                m.getRegistradoPor() != null ? m.getRegistradoPor().getNombreCompleto() : null,
                                m.getVentaId(),
                                m.getCreadoEn()))
                        .toList();

        return new SesionCajaRespuestaDTO(
                sesion.getId(),
                sesion.getCajera().getNombreCompleto(),
                sesion.getSaldoInicialCop(),
                sesion.getSaldoFinalCop(),
                sesion.getTotalVentasCop(),
                sesion.getTotalEfectivoCop(),
                sesion.getTotalTransferenciaCop(),
                sesion.getTotalCreditoCop(),
                sesion.getTotalGastosCop(),
                sesion.getTotalAbonosCreditoCop(),
                sesion.getDiferenciaCop(),
                saldoEsperado,
                sesion.isEstaAbierta(),
                sesion.getAbiertaEn(),
                sesion.getCerradaEn(),
                sesion.getNotasCierre(),
                movimientosDTO);
    }
}