package com.sgi.auto.autenticacion;

import com.sgi.auto.autenticacion.dto.LoginSolicitudDTO;
import com.sgi.auto.autenticacion.dto.OlvideContrasenaDTO;
import com.sgi.auto.autenticacion.dto.RestablecerContrasenaDTO;
import com.sgi.auto.autenticacion.dto.TokenRespuestaDTO;
import com.sgi.auto.compartido.EmailServicio;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServicio {

    private final AuthenticationManager gestorAutenticacion;
    private final JwtUtil jwtUtil;
    private final UsuarioRepositorio usuarioRepositorio;
    private final CodigoRecuperacionRepositorio codigoRecuperacionRepositorio;
    private final EmailServicio emailServicio;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MINUTOS_VALIDEZ_CODIGO = 15;
    private static final int SEGUNDOS_ENTRE_SOLICITUDES = 60;

    @Transactional
    public TokenRespuestaDTO ingresar(LoginSolicitudDTO solicitud) {
        // Acepta nombreUsuario o correo en el mismo campo.
        Usuario usuario = usuarioRepositorio
                .buscarPorUsuarioOCorreo(solicitud.nombreUsuario())
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        if (usuario.estaBloqueado()) {
            throw new BadCredentialsException(
                    "Cuenta bloqueada temporalmente. Intente de nuevo más tarde.");
        }

        try {
            // Siempre se autentica con el nombreUsuario REAL ya resuelto
            // arriba, sin importar si la persona escribió su usuario o
            // su correo en el formulario de login.
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

    /**
     * Solicita un código de recuperación. Por seguridad, SIEMPRE responde
     * igual (no revela si el usuario/correo existe o no en el sistema).
     */
    @Transactional
    public void olvideContrasena(OlvideContrasenaDTO solicitud) {
        usuarioRepositorio.buscarPorUsuarioOCorreo(solicitud.identificador())
                .ifPresent(usuario -> {
                    codigoRecuperacionRepositorio
                            .findFirstByUsuarioIdOrderByCreadoEnDesc(usuario.getId())
                            .ifPresent(ultimo -> {
                                long segundosDesdeUltimo = Duration.between(
                                        ultimo.getCreadoEn(), OffsetDateTime.now()).getSeconds();
                                if (segundosDesdeUltimo < SEGUNDOS_ENTRE_SOLICITUDES) {
                                    throw new ReglaNegocioExcepcion(
                                            "Ya se envió un código recientemente. Espera un momento antes de pedir otro.");
                                }
                            });

                    String codigo = generarCodigo();

                    CodigoRecuperacion nuevoCodigo = CodigoRecuperacion.builder()
                            .usuario(usuario)
                            .codigo(codigo)
                            .expiraEn(OffsetDateTime.now().plusMinutes(MINUTOS_VALIDEZ_CODIGO))
                            .build();
                    codigoRecuperacionRepositorio.save(nuevoCodigo);

                    String cuerpo = """
                            <p>Hola %s,</p>
                            <p>Tu código para restablecer tu contraseña es:</p>
                            <h2>%s</h2>
                            <p>Este código es válido por %d minutos. Si no solicitaste este código, ignora este correo.</p>
                            """.formatted(usuario.getNombreCompleto(), codigo, MINUTOS_VALIDEZ_CODIGO);

                    emailServicio.enviar(usuario.getCorreo(),
                            "Código para restablecer tu contraseña", cuerpo);

                    log.info("Código de recuperación generado para usuario id={}", usuario.getId());
                });
    }

    @Transactional
    public void restablecerContrasena(RestablecerContrasenaDTO solicitud) {
        Usuario usuario = usuarioRepositorio
                .buscarPorUsuarioOCorreo(solicitud.identificador())
                .orElseThrow(() -> new ReglaNegocioExcepcion("Código inválido o expirado"));

        CodigoRecuperacion codigoValido = codigoRecuperacionRepositorio
                .buscarValido(usuario.getId(), solicitud.codigo(), OffsetDateTime.now())
                .orElseThrow(() -> new ReglaNegocioExcepcion("Código inválido o expirado"));

        usuario.setContrasenaHash(passwordEncoder.encode(solicitud.nuevaContrasena()));
        usuario.registrarIngresoExitoso();
        usuarioRepositorio.save(usuario);

        codigoValido.setUsado(true);
        codigoRecuperacionRepositorio.save(codigoValido);

        log.info("Contraseña restablecida para usuario id={}", usuario.getId());
    }

    private String generarCodigo() {
        int numero = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(numero);
    }
}