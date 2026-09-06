package com.sgi.auto.autenticacion;

import com.sgi.auto.autenticacion.dto.LoginSolicitudDTO;
import com.sgi.auto.autenticacion.dto.OlvideContrasenaDTO;
import com.sgi.auto.autenticacion.dto.RestablecerContrasenaDTO;
import com.sgi.auto.autenticacion.dto.TokenRespuestaDTO;
import com.sgi.auto.compartido.ApiRespuesta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/autenticacion")
@RequiredArgsConstructor
public class AuthContolador {

    private final AuthServicio authServicio;

    @PostMapping("/ingresar")
    public ResponseEntity<ApiRespuesta<TokenRespuestaDTO>> ingresar(
            @Valid @RequestBody LoginSolicitudDTO solicitud) {

        TokenRespuestaDTO respuesta = authServicio.ingresar(solicitud);
        return ResponseEntity.ok(ApiRespuesta.exitoso(respuesta, "Ingreso exitoso"));
    }

    @PostMapping("/olvide-contrasena")
    public ResponseEntity<ApiRespuesta<Void>> olvideContrasena(
            @Valid @RequestBody OlvideContrasenaDTO solicitud) {

        authServicio.olvideContrasena(solicitud);
        // Mensaje siempre igual, exista o no el usuario/correo — por seguridad.
        return ResponseEntity.ok(ApiRespuesta.exitoso(null,
                "Te enviamos un código a tu correo. Revisa tu bandeja de entrada (y la carpeta de spam, por si acaso)."));
    }

    @PostMapping("/restablecer-contrasena")
    public ResponseEntity<ApiRespuesta<Void>> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaDTO solicitud) {

        authServicio.restablecerContrasena(solicitud);
        return ResponseEntity.ok(ApiRespuesta.exitoso(null,
                "Contraseña restablecida correctamente"));
    }
}