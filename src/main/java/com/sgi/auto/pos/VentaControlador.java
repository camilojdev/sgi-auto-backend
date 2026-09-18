package com.sgi.auto.pos;

import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.pos.dto.AnulacionDTO;
import com.sgi.auto.pos.dto.VentaCrearDTO;
import com.sgi.auto.pos.dto.VentaRespuestaDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DUENO','CAJERA')")
public class VentaControlador {

    private final VentaServicio ventaServicio;
    private final FacturaPdfServicio facturaPdfServicio;

    @PostMapping
    public ResponseEntity<ApiRespuesta<VentaRespuestaDTO>> crear(
            @Valid @RequestBody VentaCrearDTO solicitud) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiRespuesta.exitoso(
                        ventaServicio.crear(solicitud),
                        "Venta registrada correctamente"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiRespuesta<VentaRespuestaDTO>> obtenerPorId(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(ventaServicio.obtenerPorId(id)));
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<VentaRespuestaDTO>> anular(
            @PathVariable Long id,
            @Valid @RequestBody AnulacionDTO solicitud) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                ventaServicio.anular(id, solicitud),
                "Venta anulada correctamente"));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<ApiRespuesta<Page<VentaRespuestaDTO>>> porCliente(
            @PathVariable Long clienteId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                ventaServicio.ventasPorCliente(clienteId, pageable)));
    }

    @GetMapping("/hoy")
    public ResponseEntity<ApiRespuesta<Page<VentaRespuestaDTO>>> hoy(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                ventaServicio.ventasDeHoy(pageable)));
    }

    @GetMapping(value = "/{id}/factura", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarFactura(@PathVariable Long id) {
        byte[] pdf = facturaPdfServicio.generar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=factura-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
