package com.sgi.auto.usuarios;

import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.usuarios.dto.CambiarContrasenaDTO;
import com.sgi.auto.usuarios.dto.PermisosActualizarDTO;
import com.sgi.auto.usuarios.dto.UsuarioCrearDTO;
import com.sgi.auto.usuarios.dto.UsuarioRespuestaDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sgi.auto.usuarios.dto.SolicitarCreacionDuenoDTO;
import com.sgi.auto.usuarios.dto.ConfirmarCreacionDuenoDTO;
import java.util.List;


@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DUENO')")
public class UsuarioControlador {

    private final UsuarioServicio usuarioServicio;

    @PostMapping
    public ResponseEntity<ApiRespuesta<UsuarioRespuestaDTO>> crear(
            @Valid @RequestBody UsuarioCrearDTO solicitud) {

        UsuarioRespuestaDTO creado = usuarioServicio.crear(solicitud);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiRespuesta.exitoso(creado, "Usuario creado correctamente"));
    }

    @PatchMapping("/{id}/contrasena")
    @PreAuthorize("hasRole('DUENO') or #id == authentication.principal.id")
    public ResponseEntity<ApiRespuesta<Void>> cambiarContrasena(
            @PathVariable Long id,
            @Valid @RequestBody CambiarContrasenaDTO solicitud) {
        usuarioServicio.cambiarContrasena(id, solicitud);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Contraseña actualizada correctamente"));
    }

    @GetMapping
    public ResponseEntity<ApiRespuesta<List<UsuarioRespuestaDTO>>> listarTodos() {
        return ResponseEntity.ok(ApiRespuesta.exitoso(usuarioServicio.listarTodos()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiRespuesta<UsuarioRespuestaDTO>> obtenerPorId(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(usuarioServicio.obtenerPorId(id)));
    }

    @PatchMapping("/{id}/permisos")
    public ResponseEntity<ApiRespuesta<UsuarioRespuestaDTO>> actualizarPermisos(
            @PathVariable Long id,
            @Valid @RequestBody PermisosActualizarDTO permisos) {

        UsuarioRespuestaDTO actualizado = usuarioServicio.actualizarPermisos(id, permisos);
        return ResponseEntity.ok(
                ApiRespuesta.exitoso(actualizado, "Permisos actualizados correctamente"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiRespuesta<Void>> desactivar(@PathVariable Long id) {
        usuarioServicio.desactivar(id);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Usuario desactivado correctamente"));
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<ApiRespuesta<UsuarioRespuestaDTO>> reactivar(@PathVariable Long id) {
        UsuarioRespuestaDTO reactivado = usuarioServicio.reactivar(id);
        return ResponseEntity.ok(ApiRespuesta.exitoso(reactivado, "Usuario reactivado correctamente"));
    }

    @PostMapping("/solicitar-creacion-dueno")
    public ResponseEntity<ApiRespuesta<Void>> solicitarCreacionDueno(
            @AuthenticationPrincipal Usuario solicitante,
            @Valid @RequestBody SolicitarCreacionDuenoDTO dto) {
        usuarioServicio.solicitarCreacionDueno(solicitante.getId(), dto);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null,
                "Te enviamos un código de confirmación a tu correo registrado"));
    }

    @PostMapping("/confirmar-creacion-dueno")
    public ResponseEntity<ApiRespuesta<UsuarioRespuestaDTO>> confirmarCreacionDueno(
            @AuthenticationPrincipal Usuario solicitante,
            @Valid @RequestBody ConfirmarCreacionDuenoDTO dto) {
        UsuarioRespuestaDTO creado = usuarioServicio.confirmarCreacionDueno(solicitante.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiRespuesta.exitoso(creado, "Usuario DUEÑO creado correctamente"));
    }
}