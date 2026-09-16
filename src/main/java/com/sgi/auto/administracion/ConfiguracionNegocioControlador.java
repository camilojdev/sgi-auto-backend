package com.sgi.auto.administracion;

import com.sgi.auto.administracion.dto.ConfiguracionNegocioActualizarDTO;
import com.sgi.auto.administracion.dto.ConfiguracionNegocioRespuestaDTO;
import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.usuarios.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/configuracion-negocio")
@RequiredArgsConstructor
public class ConfiguracionNegocioControlador {

    private final ConfiguracionNegocioServicio servicio;

    @GetMapping
    public ResponseEntity<ApiRespuesta<ConfiguracionNegocioRespuestaDTO>> obtener() {
        return ResponseEntity.ok(ApiRespuesta.exitoso(servicio.obtener()));
    }

    @PutMapping
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<ConfiguracionNegocioRespuestaDTO>> actualizar(
            @Valid @RequestBody ConfiguracionNegocioActualizarDTO dto,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                servicio.actualizar(dto, usuario.getId()), "Configuración del negocio actualizada"));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DUENO')")
    public ResponseEntity<ApiRespuesta<ConfiguracionNegocioRespuestaDTO>> actualizarLogo(
            @RequestParam("archivo") MultipartFile archivo,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                servicio.actualizarLogo(archivo, usuario.getId()), "Logo actualizado"));
    }
}