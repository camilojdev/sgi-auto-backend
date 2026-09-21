package com.sgi.auto.inventario;

import com.sgi.auto.compartido.ConflictoExcepcion;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.*;
import com.sgi.auto.usuarios.Usuario;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServicio {

    private final ProductoRepositorio productoRepositorio;
    private final ProductoCodigoRepositorio productoCodigoRepositorio;
    private final MovimientoStockRepositorio movimientoStockRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final ProveedorRepositorio proveedorRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    //private final EntradaMercanciaRepositorio entradaMercanciaRepositorio;
    private final ProductoMapper productoMapper;

    // ── Productos ────────────────────────────────────────────────

    @Transactional
    public ProductoRespuestaDTO crearProducto(ProductoCrearDTO solicitud) {
        if (productoRepositorio.existePorCodigo(solicitud.codigo())) {
            throw new ConflictoExcepcion(
                    "Ya existe un producto con el código: " + solicitud.codigo());
        }

        if (solicitud.numeroInterno() != null && !solicitud.numeroInterno().isBlank()
                && productoRepositorio.existePorNumeroInterno(solicitud.numeroInterno())) {
            throw new ConflictoExcepcion(
                    "Ya existe un producto con el número interno: " + solicitud.numeroInterno());
        }

        Producto producto = productoMapper.aEntidad(solicitud);
        producto.setPrecioVentaDetal(solicitud.precioVentaCop());
        producto.setPrecioVentaMayor(solicitud.precioVentaCop());
        producto.setPrecioCompraSinIva(solicitud.precioCompraConIva());
        producto.setStockActual(solicitud.stockActual());
        if (solicitud.categoriaId() != null) {
            Categoria categoria = categoriaRepositorio.findById(solicitud.categoriaId())
                    .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                            "No se encontró la categoría con id: " + solicitud.categoriaId()));
            producto.setCategoria(categoria);
        }

        if (solicitud.proveedorId() != null) {
            Proveedor proveedor = proveedorRepositorio.findById(solicitud.proveedorId())
                    .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                            "No se encontró el proveedor con id: " + solicitud.proveedorId()));
            producto.setProveedor(proveedor);
        }

        Producto guardado = productoRepositorio.save(producto);

        productoCodigoRepositorio.save(ProductoCodigo.builder()
                .producto(guardado)
                .codigo(guardado.getCodigo())
                .activo(true)
                .cambiadoPor(obtenerUsuarioActual())
                .build());

        log.info("Producto creado: codigo={}, nombre={}", guardado.getCodigo(), guardado.getNombre());
        return productoMapper.aDTO(guardado);
    }

    @Transactional
    public void desactivarProducto(Long id) {
        Producto producto = productoRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el producto con id: " + id));
        producto.setEstaActivo(false);
        productoRepositorio.save(producto);
        log.info("Producto desactivado: id={}, nombre={}", id, producto.getNombre());
    }

    @Transactional(readOnly = true)
    public Page<ProductoRespuestaDTO> listarTodos(Pageable pageable) {
        return productoRepositorio.listarActivos(pageable)
                .map(productoMapper::aDTO);
    }

    @Transactional(readOnly = true)
    public ProductoRespuestaDTO obtenerPorId(Long id) {
        return productoMapper.aDTO(buscarProductoOLanzar(id));
    }

    @Transactional(readOnly = true)
    public ProductoRespuestaDTO obtenerPorCodigo(String codigo) {
        return productoRepositorio.buscarPorCodigo(codigo)
                .map(productoMapper::aDTO)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el producto con código: " + codigo));
    }

    @Transactional(readOnly = true)
    public List<ProductoRespuestaDTO> buscar(String termino) {
        if (termino == null || termino.trim().length() < 2) {
            return List.of();
        }
        return productoRepositorio.buscarPorNombre(termino.trim())
                .stream()
                .map(productoMapper::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoRespuestaDTO> listarConStockBajoMinimo() {
        return productoRepositorio.listarConStockBajoMinimo()
                .stream()
                .map(productoMapper::aDTO)
                .toList();
    }

    // ── Ajuste Manual de Inventario ──────────────────

    @Transactional
    public ProductoRespuestaDTO ajustarStock(Long productoId, AjusteStockDTO solicitud) {
        Producto producto = buscarProductoOLanzar(productoId);

        int stockAntes = producto.getStockActual();
        int stockNuevo = stockAntes + solicitud.cantidad();

        if (stockNuevo < 0) {
            throw new ReglaNegocioExcepcion(
                    "El ajuste dejaría el stock en negativo. Stock actual: "
                            + stockAntes + ", ajuste: " + solicitud.cantidad());
        }

        producto.setStockActual(stockNuevo);
        productoRepositorio.save(producto);

        registrarMovimiento(producto, TipoMovimientoStock.AJUSTE,
                Math.abs(solicitud.cantidad()), stockAntes, stockNuevo,
                null, solicitud.notas());

        log.info("Ajuste de stock: producto={}, antes={}, despues={}",
                producto.getCodigo(), stockAntes, stockNuevo);

        return productoMapper.aDTO(producto);
    }

    // ── Kardex ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<KardexRespuestaDTO> obtenerKardex(Long productoId, Pageable pageable) {
        buscarProductoOLanzar(productoId);
        return movimientoStockRepositorio
                .kardexPorProducto(productoId, pageable)
                .map(this::aKardexDTO);
    }

    // ── Categorías ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categoriaRepositorio.listarActivas();
    }

    @Transactional
    public Categoria crearCategoria(String nombre, String descripcion) {
        if (categoriaRepositorio.existePorNombre(nombre)) {
            throw new ConflictoExcepcion(
                    "Ya existe una categoría con el nombre: " + nombre);
        }
        return categoriaRepositorio.save(
                Categoria.builder().nombre(nombre).descripcion(descripcion).build());
    }

    // ── Proveedores ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<Proveedor> listarProveedores() {
        return proveedorRepositorio.listarActivos();
    }

    @Transactional
    public Proveedor crearProveedor(Proveedor proveedor) {
        if (proveedor.getNit() != null
                && proveedorRepositorio.existePorNit(proveedor.getNit())) {
            throw new ConflictoExcepcion(
                    "Ya existe un proveedor con NIT: " + proveedor.getNit());
        }
        return proveedorRepositorio.save(proveedor);
    }

    @Transactional
    public ProductoRespuestaDTO actualizarProducto(Long id, ProductoCrearDTO solicitud) {
        Producto producto = buscarProductoOLanzar(id);

        // ── Cambio de código: mantiene historial, nunca se sobrescribe sin dejar rastro ──
        if (!producto.getCodigo().equals(solicitud.codigo())) {
            if (productoRepositorio.existePorCodigoExcluyendoId(solicitud.codigo(), id)) {
                throw new ConflictoExcepcion(
                        "Ya existe un producto con el código: " + solicitud.codigo());
            }
            if (productoCodigoRepositorio.existsByCodigoAndProductoIdNot(solicitud.codigo(), id)) {
                throw new ConflictoExcepcion(
                        "Ese código ya fue utilizado anteriormente por otro producto");
            }

            productoCodigoRepositorio.findByProductoIdAndActivoTrue(id)
                    .ifPresent(anterior -> {
                        anterior.setActivo(false);
                        productoCodigoRepositorio.save(anterior);
                    });

            productoCodigoRepositorio.save(ProductoCodigo.builder()
                    .producto(producto)
                    .codigo(solicitud.codigo())
                    .activo(true)
                    .cambiadoPor(obtenerUsuarioActual())
                    .build());

            producto.setCodigo(solicitud.codigo());
            log.info("Código de producto cambiado: productoId={}, nuevoCodigo={}", id, solicitud.codigo());
        }

        // ── Número interno: se asigna una sola vez, nunca cambia ──
        String numeroInternoNuevo = solicitud.numeroInterno();
        if (producto.getNumeroInterno() != null) {
            if (numeroInternoNuevo != null && !numeroInternoNuevo.isBlank()
                    && !numeroInternoNuevo.equals(producto.getNumeroInterno())) {
                throw new ReglaNegocioExcepcion(
                        "El número interno no se puede modificar una vez asignado");
            }
        } else if (numeroInternoNuevo != null && !numeroInternoNuevo.isBlank()) {
            if (productoRepositorio.existePorNumeroInternoExcluyendoId(numeroInternoNuevo, id)) {
                throw new ConflictoExcepcion(
                        "Ya existe un producto con el número interno: " + numeroInternoNuevo);
            }
            producto.setNumeroInterno(numeroInternoNuevo);
        }

        producto.setNombre(solicitud.nombre());
        producto.setDescripcion(solicitud.descripcion());
        producto.setUnidadMedida(solicitud.unidadMedida());
        producto.setPrecioCompraConIva(solicitud.precioCompraConIva());
        producto.setPrecioCompraSinIva(solicitud.precioCompraConIva());
        producto.setPrecioVentaDetal(solicitud.precioVentaCop());
        producto.setPrecioVentaMayor(solicitud.precioVentaCop());
        producto.setStockMinimo(solicitud.stockMinimo());
        producto.setMostrarEnListaPrecios(solicitud.mostrarEnListaPrecios());
        producto.setPrecioOculto(solicitud.precioOculto());

        if (solicitud.categoriaId() != null) {
            Categoria categoria = categoriaRepositorio.findById(solicitud.categoriaId())
                    .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                            "No se encontró la categoría con id: " + solicitud.categoriaId()));
            producto.setCategoria(categoria);
        } else {
            producto.setCategoria(null);
        }

        return productoMapper.aDTO(productoRepositorio.save(producto));
    }

    // ── Historial de códigos ──────────────────────────

    @Transactional(readOnly = true)
    public List<ProductoCodigoRespuestaDTO> obtenerHistorialCodigos(Long productoId) {
        buscarProductoOLanzar(productoId);
        return productoCodigoRepositorio.findByProductoIdOrderByFechaCreacionDesc(productoId)
                .stream()
                .map(this::aHistorialDTO)
                .toList();
    }

    // ── Helpers privados ──────────────────────────────────────

    private Producto buscarProductoOLanzar(Long id) {
        return productoRepositorio.findById(id)
                .filter(p -> p.getEliminadoEn() == null)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el producto con id: " + id));
    }

    private Usuario obtenerUsuarioActual() {
        String nombreUsuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepositorio.buscarPorNombreUsuario(nombreUsuario).orElse(null);
    }

    private void registrarMovimiento(
            Producto producto,
            TipoMovimientoStock tipo,
            int cantidad,
            int stockAntes,
            int stockDespues,
            BigDecimal costoUnitario,
            String notas) {

        MovimientoStock movimiento = MovimientoStock.builder()
                .producto(producto)
                .tipoMovimiento(tipo)
                .cantidad(cantidad)
                .stockAntes(stockAntes)
                .stockDespues(stockDespues)
                .costoUnitarioCop(costoUnitario)
                .notas(notas)
                .build();

        movimientoStockRepositorio.save(movimiento);
    }

    private KardexRespuestaDTO aKardexDTO(MovimientoStock m) {
        return new KardexRespuestaDTO(
                m.getId(),
                m.getTipoMovimiento(),
                m.getCantidad(),
                m.getStockAntes(),
                m.getStockDespues(),
                m.getCostoUnitarioCop(),
                m.getNotas(),
                m.getRegistradoPor() != null
                        ? m.getRegistradoPor().getNombreCompleto()
                        : "Sistema",
                m.getCreadoEn());
    }

    private ProductoCodigoRespuestaDTO aHistorialDTO(ProductoCodigo c) {
        return new ProductoCodigoRespuestaDTO(
                c.getId(),
                c.getCodigo(),
                c.isActivo(),
                c.getFechaCreacion(),
                c.getCambiadoPor() != null ? c.getCambiadoPor().getNombreCompleto() : "Sistema");
    }
}