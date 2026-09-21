package com.sgi.auto.inventario;

import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.inventario.dto.GenerarEtiquetasDTO;
import com.sgi.auto.inventario.dto.PlantillaEtiquetaActualizarDTO;
import com.sgi.auto.inventario.dto.PlantillaEtiquetaRespuestaDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventario/etiquetas")
@RequiredArgsConstructor
public class EtiquetaControlador {

    private final EtiquetaPdfServicio etiquetaPdfServicio;
    private final PlantillaEtiquetaServicio plantillaEtiquetaServicio;

    @GetMapping("/plantillas")
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<ApiRespuesta<List<PlantillaEtiquetaRespuestaDTO>>> listarPlantillas() {
        return ResponseEntity.ok(ApiRespuesta.exitoso(plantillaEtiquetaServicio.listar()));
    }

    @PutMapping("/plantillas/{codigo}")
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<PlantillaEtiquetaRespuestaDTO>> actualizarPlantilla(
            @PathVariable String codigo,
            @Valid @RequestBody PlantillaEtiquetaActualizarDTO dto) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                plantillaEtiquetaServicio.actualizar(codigo, dto), "Plantilla actualizada correctamente"));
    }

    @PostMapping(value = "/generar", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('DUENO','CAJERA')")
    public ResponseEntity<byte[]> generar(@Valid @RequestBody GenerarEtiquetasDTO solicitud) {
        byte[] pdf = etiquetaPdfServicio.generar(solicitud);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=etiquetas.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}