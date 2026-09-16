package com.sgi.auto.perfil;

import com.sgi.auto.compartido.ApiRespuesta;
import com.sgi.auto.perfil.dto.*;
import com.sgi.auto.usuarios.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilControlador {

    private final PerfilServicio perfilServicio;

    @GetMapping
    public ResponseEntity<ApiRespuesta<PerfilRespuestaDTO>> obtener(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(perfilServicio.obtener(usuario.getId())));
    }

    @PatchMapping("/nombre")
    public ResponseEntity<ApiRespuesta<PerfilRespuestaDTO>> actualizarNombre(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody ActualizarNombreDTO dto) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                perfilServicio.actualizarNombre(usuario.getId(), dto), "Nombre actualizado correctamente"));
    }

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiRespuesta<PerfilRespuestaDTO>> actualizarFoto(
            @AuthenticationPrincipal Usuario usuario, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                perfilServicio.actualizarFoto(usuario.getId(), archivo), "Foto de perfil actualizada"));
    }

    @DeleteMapping("/foto")
    public ResponseEntity<ApiRespuesta<PerfilRespuestaDTO>> eliminarFoto(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                perfilServicio.eliminarFoto(usuario.getId()), "Foto de perfil eliminada"));
    }

    @PatchMapping("/contrasena")
    public ResponseEntity<ApiRespuesta<Void>> cambiarContrasenaConActual(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody CambiarContrasenaConActualDTO dto) {
        perfilServicio.cambiarContrasenaConActual(usuario.getId(), dto);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Contraseña actualizada correctamente"));
    }

    @PostMapping("/contrasena/solicitar-codigo")
    public ResponseEntity<ApiRespuesta<Void>> solicitarCodigoCambioContrasena(@AuthenticationPrincipal Usuario usuario) {
        perfilServicio.solicitarCodigoCambioContrasena(usuario.getId());
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Te enviamos un código a tu correo registrado"));
    }

    @PostMapping("/contrasena/confirmar-codigo")
    public ResponseEntity<ApiRespuesta<Void>> confirmarCambioContrasenaConCodigo(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody ConfirmarCambioContrasenaDTO dto) {
        perfilServicio.confirmarCambioContrasenaConCodigo(usuario.getId(), dto);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Contraseña actualizada correctamente"));
    }

    @PostMapping("/correo/solicitar-cambio")
    public ResponseEntity<ApiRespuesta<Void>> solicitarCambioCorreo(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody SolicitarCambioCorreoDTO dto) {
        perfilServicio.solicitarCambioCorreo(usuario.getId(), dto);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null, "Te enviamos un código a tu correo nuevo para confirmarlo"));
    }

    @PostMapping("/correo/confirmar-cambio")
    public ResponseEntity<ApiRespuesta<PerfilRespuestaDTO>> confirmarCambioCorreo(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody ConfirmarCambioCorreoDTO dto) {
        return ResponseEntity.ok(ApiRespuesta.exitoso(
                perfilServicio.confirmarCambioCorreo(usuario.getId(), dto), "Correo actualizado correctamente"));
    }
}