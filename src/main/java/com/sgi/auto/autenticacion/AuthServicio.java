package com.sgi.auto.autenticacion;

import com.sgi.auto.autenticacion.dto.LoginSolicitudDTO;
import com.sgi.auto.autenticacion.dto.OlvideContrasenaDTO;
import com.sgi.auto.autenticacion.dto.RestablecerContrasenaDTO;
import com.sgi.auto.autenticacion.dto.TokenRespuestaDTO;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.usuarios.Usuario;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServicio {

    private final AuthenticationManager gestorAutenticacion;
    private final JwtUtil jwtUtil;
    private final UsuarioRepositorio usuarioRepositorio;
    private final CodigoVerificacionServicio codigoVerificacionServicio;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenRespuestaDTO ingresar(LoginSolicitudDTO solicitud) {
        Usuario usuario = usuarioRepositorio
                .buscarPorUsuarioOCorreo(solicitud.nombreUsuario())
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        if (usuario.estaBloqueado()) {
            throw new BadCredentialsException(
                    "Cuenta bloqueada temporalmente. Intente de nuevo más tarde.");
        }

        try {
            gestorAutenticacion.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            usuario.getNombreUsuario(),
                            solicitud.contrasena()));

        } catch (AuthenticationException ex) {
            usuario.registrarIntentoFallido();
            usuarioRepositorio.save(usuario);
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        usuario.registrarIngresoExitoso();
        usuarioRepositorio.save(usuario);

        Map<String, Object> permisos = Map.of(
                "puedeAplicarDescuento", usuario.isPuedeAplicarDescuento(),
                "puedeAnularVenta",      usuario.isPuedeAnularVenta(),
                "puedeCerrarCaja",       usuario.isPuedeCerrarCaja(),
                "puedeVerReportes",      usuario.isPuedeVerReportes(),
                "puedeGestionarCredito", usuario.isPuedeGestionarCredito()
        );

        String token = jwtUtil.generarToken(
                usuario.getNombreUsuario(),
                usuario.getRol().name(),
                permisos);

        log.info("Ingreso exitoso: usuario={}, rol={}", usuario.getNombreUsuario(), usuario.getRol());

        return new TokenRespuestaDTO(
                token,
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                permisos);
    }

    @Transactional
    public void olvideContrasena(OlvideContrasenaDTO solicitud) {
        usuarioRepositorio.buscarPorUsuarioOCorreo(solicitud.identificador())
                .ifPresent(usuario -> codigoVerificacionServicio.generarYEnviar(
                        usuario,
                        TipoCodigoRecuperacion.RECUPERACION_CONTRASENA,
                        usuario.getCorreo(),
                        null,
                        "Código para restablecer tu contraseña",
                        """
                        <p>Hola %s,</p>
                        <p>Tu código para restablecer tu contraseña es:</p>
                        <h2>%s</h2>
                        <p>Este código es válido por %d minutos. Si no solicitaste este código, ignora este correo.</p>
                        """
                ));
    }

    @Transactional
    public void restablecerContrasena(RestablecerContrasenaDTO solicitud) {
        Usuario usuario = usuarioRepositorio
                .buscarPorUsuarioOCorreo(solicitud.identificador())
                .orElseThrow(() -> new ReglaNegocioExcepcion("Código inválido o expirado"));

        codigoVerificacionServicio.validarYConsumir(
                usuario.getId(), solicitud.codigo(), TipoCodigoRecuperacion.RECUPERACION_CONTRASENA);

        usuario.setContrasenaHash(passwordEncoder.encode(solicitud.nuevaContrasena()));
        usuario.registrarIngresoExitoso();
        usuarioRepositorio.save(usuario);

        log.info("Contraseña restablecida para usuario id={}", usuario.getId());
    }
}