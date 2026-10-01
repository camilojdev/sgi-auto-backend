package com.sgi.auto.inventario;

import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.inventario.dto.AjusteStockDTO;
import com.sgi.auto.inventario.dto.KardexRespuestaDTO;
import com.sgi.auto.inventario.dto.ProductoCodigoRespuestaDTO;
import com.sgi.auto.inventario.dto.ProductoCrearDTO;
import com.sgi.auto.inventario.dto.ProductoIdentificadoDTO;
import com.sgi.auto.inventario.dto.ProductoRespuestaDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/productos")
@RequiredArgsConstructor
public class ProductoControlador {

    private final ProductoServicio productoServicio;
    private final BarcodeService barcodeService;

    @PostMapping
    @PreAuthorize("hasRole('DUENO') or (hasRole('CAJERA') and authentication.principal.puedeGestionarInventario)")
    public ResponseEntity<ApiRespuesta<ProductoRespuestaDTO>> crear(
            @Valid @RequestBody ProductoCrearDTO solicitud) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiRespuesta.exitoso(
                        productoServicio.crearProducto(solicitud),
                        "Producto creado correctamente"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DUENO','CAJERA','MECANICO') and " +
            "(!#incluirInactivos or hasRole('DUENO') or authentication.principal.puedeGestionarInventario)")
    public ResponseEntity<ApiRespuesta<Page<ProductoRespuestaDTO>>> listar(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(productoServicio.listarTodos(pageable, incluirInactivos)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DUENO') or (hasRole('CAJERA') and authentication.principal.puedeGestionarInventario)")
    public ResponseEntity<ApiRespuesta<Void>> eliminar(@PathVariable Long id) {
        productoServicio.desactivarProducto(id);
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(null, "Producto desactivado correctamente"));
    }

    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('DUENO') or (hasRole('CAJERA') and authentication.principal.puedeGestionarInventario)")
    public ResponseEntity<ApiRespuesta<ProductoRespuestaDTO>> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                productoServicio.reactivarProducto(id), "Producto reactivado correctamente"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA','MECANICO')")
    public ResponseEntity<ApiRespuesta<ProductoRespuestaDTO>> obtenerPorId(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(productoServicio.obtenerPorId(id)));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA','MECANICO')")
    public ResponseEntity<ApiRespuesta<List<ProductoRespuestaDTO>>> buscar(
            @RequestParam String q) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(productoServicio.buscar(q)));
    }

    @GetMapping("/identificar")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA','MECANICO')")
    public ResponseEntity<ApiRespuesta<ProductoIdentificadoDTO>> identificar(
            @RequestParam String codigo) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(barcodeService.identificarProducto(codigo)));
    }

    @GetMapping("/{id}/historial-codigos")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<ApiRespuesta<List<ProductoCodigoRespuestaDTO>>> historialCodigos(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(productoServicio.obtenerHistorialCodigos(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<ApiRespuesta<ProductoRespuestaDTO>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProductoCrearDTO solicitud) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(
                        productoServicio.actualizarProducto(id, solicitud),
                        "Producto actualizado correctamente"));
    }

    @PostMapping("/{id}/ajustar-stock")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<ApiRespuesta<ProductoRespuestaDTO>> ajustarStock(
            @PathVariable Long id,
            @Valid @RequestBody AjusteStockDTO solicitud) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(
                        productoServicio.ajustarStock(id, solicitud),
                        "Stock ajustado correctamente"));
    }

    @GetMapping("/{id}/kardex")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<ApiRespuesta<Page<KardexRespuestaDTO>>> kardex(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(productoServicio.obtenerKardex(id, pageable)));
    }
}