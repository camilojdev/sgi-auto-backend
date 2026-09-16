package com.sgi.auto.autenticacion;

import static org.junit.jupiter.api.Assertions.*;
import com.sgi.auto.autenticacion.dto.LoginSolicitudDTO;
import com.sgi.auto.autenticacion.dto.TokenRespuestaDTO;
import com.sgi.auto.usuarios.RolUsuario;
import com.sgi.auto.usuarios.Usuario;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServicio — pruebas unitarias")
class AuthServicioPrueba {

    @Mock AuthenticationManager gestorAutenticacion;
    @Mock JwtUtil jwtUtil;
    @Mock UsuarioRepositorio usuarioRepositorio;

    @InjectMocks AuthServicio authServicio;

    private Usuario usuarioPrueba;

    @BeforeEach
    void configurar() {
        usuarioPrueba = Usuario.builder()
                .nombreCompleto("Carlos Ramírez")
                .nombreUsuario("admin")
                .contrasenaHash("$2a$12$hash")
                .rol(RolUsuario.DUENO)
                .estaActivo(true)
                .build();
    }

    @Test
    @DisplayName("Ingreso exitoso devuelve token JWT con el rol correcto")
    void ingresoExitoso_devuelveToken() {
        // Arrange
        when(usuarioRepositorio.buscarPorUsuarioOCorreo("admin"))
                .thenReturn(Optional.of(usuarioPrueba));
        when(jwtUtil.generarToken(anyString(), anyString(), anyMap()))
                .thenReturn("token.jwt.firmado");
        when(gestorAutenticacion.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));

        LoginSolicitudDTO solicitud = new LoginSolicitudDTO("admin", "Admin2026!");

        // Act
        TokenRespuestaDTO resultado = authServicio.ingresar(solicitud);

        // Assert
        assertThat(resultado.token()).isEqualTo("token.jwt.firmado");
        assertThat(resultado.rol()).isEqualTo("DUENO");
        assertThat(resultado.nombreCompleto()).isEqualTo("Carlos Ramírez");
    }

    @Test
    @DisplayName("Credenciales incorrectas lanza BadCredentialsException")
    void credencialesIncorrectas_lanzaExcepcion() {
        when(usuarioRepositorio.buscarPorUsuarioOCorreo("admin"))
                .thenReturn(Optional.of(usuarioPrueba));
        doThrow(new BadCredentialsException("mal"))
                .when(gestorAutenticacion).authenticate(any());

        LoginSolicitudDTO solicitud = new LoginSolicitudDTO("admin", "contraseña_mala");

        assertThatThrownBy(() -> authServicio.ingresar(solicitud))
                .isInstanceOf(BadCredentialsException.class);

        // Verifica que se incrementaron los intentos fallidos
        assertThat(usuarioPrueba.getIntentosFallidosLogin()).isEqualTo(1);
        verify(usuarioRepositorio).save(usuarioPrueba);
    }

    @Test
    @DisplayName("Cuenta bloqueada lanza excepción sin intentar autenticar")
    void cuentaBloqueada_lanzaExcepcionSinAutenticar() {
        usuarioPrueba.setBloqueadoHasta(OffsetDateTime.now().plusMinutes(10));
        when(usuarioRepositorio.buscarPorUsuarioOCorreo("admin"))
                .thenReturn(Optional.of(usuarioPrueba));

        LoginSolicitudDTO solicitud = new LoginSolicitudDTO("admin", "cualquiera");

        assertThatThrownBy(() -> authServicio.ingresar(solicitud))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("bloqueada");

        // Spring Security no debe llamarse si la cuenta está bloqueada
        verifyNoInteractions(gestorAutenticacion);
    }

    @Test
    @DisplayName("Cinco intentos fallidos activan el bloqueo de 15 minutos")
    void cincoIntentosFallidos_activanBloqueo() {
        when(usuarioRepositorio.buscarPorUsuarioOCorreo("admin"))
                .thenReturn(Optional.of(usuarioPrueba));
        doThrow(new BadCredentialsException("mal"))
                .when(gestorAutenticacion).authenticate(any());

        LoginSolicitudDTO solicitud = new LoginSolicitudDTO("admin", "mal");

        // 5 intentos fallidos
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authServicio.ingresar(solicitud))
                    .isInstanceOf(BadCredentialsException.class);
        }

        // Tras el 5to intento, la cuenta debe estar bloqueada
        assertThat(usuarioPrueba.estaBloqueado()).isTrue();
        assertThat(usuarioPrueba.getBloqueadoHasta()).isAfter(OffsetDateTime.now());
    }
}