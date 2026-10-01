package com.sgi.auto.usuarios;

import com.sgi.auto.compartido.ConflictoExcepcion;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.usuarios.dto.PermisosActualizarDTO;
import com.sgi.auto.usuarios.dto.UsuarioCrearDTO;
import com.sgi.auto.usuarios.dto.UsuarioMapper;
import com.sgi.auto.usuarios.dto.UsuarioRespuestaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioServicio — pruebas unitarias")
class UsuarioServicioPrueba {

    @Mock
    UsuarioRepositorio usuarioRepositorio;

    @Mock
    UsuarioMapper usuarioMapper;

    @Mock
    PasswordEncoder codificadorContrasena;

    @InjectMocks
    UsuarioServicio usuarioServicio;

    private Usuario usuarioCajera;
    private UsuarioCrearDTO solicitudCrearCajera;

    @BeforeEach
    void configurar() {
        usuarioCajera = Usuario.builder()
                .nombreCompleto("María López")
                .nombreUsuario("maria.lopez")
                .contrasenaHash("$2a$12$hashSimulado")
                .rol(RolUsuario.CAJERA)
                .estaActivo(true)
                .build();

        usuarioCajera.setId(1L);

        solicitudCrearCajera = new UsuarioCrearDTO(
                "María López",
                "maria.lopez",
                "maria@taller.com",
                "ContraseñaSegura123",
                RolUsuario.CAJERA,
                true,   // puedeAplicarDescuento
                false,  // puedeAnularVenta
                true,   // puedeCerrarCaja
                false,  // puedeVerReportes
                false   // puedeGestionarCredito
        );
    }

    @Test
    @DisplayName("Crear usuario exitosamente hashea la contraseña y guarda")
    void crear_usuarioValido_seGuardaConContrasenaHasheada() {

        // Arrange
        when(usuarioRepositorio.existePorNombreUsuario("maria.lopez"))
                .thenReturn(false);

        when(usuarioMapper.aEntidad(solicitudCrearCajera))
                .thenReturn(usuarioCajera);

        when(codificadorContrasena.encode("ContraseñaSegura123"))
                .thenReturn("$2a$12$hashSimulado");

        when(usuarioRepositorio.save(any(Usuario.class)))
                .thenReturn(usuarioCajera);

        when(usuarioMapper.aDTO(usuarioCajera))
                .thenReturn(
                        new UsuarioRespuestaDTO(
                                1L,
                                "María López",
                                "maria.lopez",
                                "maria@taller.com",
                                RolUsuario.CAJERA,
                                true,
                                false,
                                true,
                                false,
                                false,
                                true,
                                null
                        )
                );

        // Act
        UsuarioRespuestaDTO resultado =
                usuarioServicio.crear(solicitudCrearCajera);

        // Assert
        assertThat(resultado.nombreUsuario())
                .isEqualTo("maria.lopez");

        assertThat(resultado.rol())
                .isEqualTo(RolUsuario.CAJERA);

        verify(codificadorContrasena)
                .encode("ContraseñaSegura123");

        verify(usuarioRepositorio)
                .save(usuarioCajera);
    }

    @Test
    @DisplayName("Crear usuario con nombre duplicado lanza ConflictoExcepcion")
    void crear_nombreUsuarioDuplicado_lanzaConflictoExcepcion() {

        when(usuarioRepositorio.existePorNombreUsuario("maria.lopez"))
                .thenReturn(true);

        assertThatThrownBy(
                () -> usuarioServicio.crear(solicitudCrearCajera)
        )
                .isInstanceOf(ConflictoExcepcion.class)
                .hasMessageContaining("Ya existe un usuario");

        // No debe intentar guardar si ya existe el nombre de usuario
        verify(usuarioRepositorio, never())
                .save(any());
    }

    @Test
    @DisplayName("Crear usuario con rol DUENO ignora los permisos granulares")
    void crear_rolDueno_ignoraPermisosGranulares() {

        UsuarioCrearDTO solicitudDueno = new UsuarioCrearDTO(
                "Carlos Ramírez",
                "carlos.r",
                "carlos@taller.com",
                "ContraseñaSegura123",
                RolUsuario.DUENO,
                true,
                true,
                true,
                true,
                true
        );

        Usuario usuarioDueno = Usuario.builder()
                .nombreCompleto("Carlos Ramírez")
                .nombreUsuario("carlos.r")
                .rol(RolUsuario.DUENO)
                .puedeAplicarDescuento(true)
                .puedeAnularVenta(true)
                .puedeCerrarCaja(true)
                .puedeVerReportes(true)
                .puedeGestionarCredito(true)
                .build();

        when(usuarioRepositorio.existePorNombreUsuario("carlos.r"))
                .thenReturn(false);

        when(usuarioMapper.aEntidad(solicitudDueno))
                .thenReturn(usuarioDueno);

        when(codificadorContrasena.encode(anyString()))
                .thenReturn("$2a$12$hash");

        when(usuarioRepositorio.save(any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(usuarioMapper.aDTO(any(Usuario.class)))
                .thenReturn(
                        new UsuarioRespuestaDTO(
                                2L,
                                "Carlos Ramírez",
                                "carlos.r",
                                "carlos@taller.com",
                                RolUsuario.DUENO,
                                false,
                                false,
                                false,
                                false,
                                false,
                                true,
                                null
                        )
                );

        usuarioServicio.crear(solicitudDueno);

        // El servicio debe haber forzado los permisos a false
        // por no ser CAJERA
        assertThat(usuarioDueno.isPuedeAplicarDescuento())
                .isFalse();

        assertThat(usuarioDueno.isPuedeAnularVenta())
                .isFalse();

        assertThat(usuarioDueno.isPuedeCerrarCaja())
                .isFalse();

        assertThat(usuarioDueno.isPuedeVerReportes())
                .isFalse();

        assertThat(usuarioDueno.isPuedeGestionarCredito())
                .isFalse();
    }

    @Test
    @DisplayName("Actualizar permisos de un usuario MECANICO lanza ReglaNegocioExcepcion")
    void actualizarPermisos_usuarioNoEsCajera_lanzaReglaNegocioExcepcion() {

        Usuario mecanico = Usuario.builder()
                .nombreCompleto("Luis Martínez")
                .nombreUsuario("luis.m")
                .rol(RolUsuario.MECANICO)
                .build();

        mecanico.setId(3L);

        when(usuarioRepositorio.findById(3L))
                .thenReturn(Optional.of(mecanico));

        /*
         * PermisosActualizarDTO ahora requiere 6 booleanos.
         */
        PermisosActualizarDTO permisos =
                new PermisosActualizarDTO(
                        true,
                        true,
                        true,
                        true,
                        true,
                        true
                );

        assertThatThrownBy(
                () -> usuarioServicio.actualizarPermisos(3L, permisos)
        )
                .isInstanceOf(ReglaNegocioExcepcion.class)
                .hasMessageContaining(
                        "solo se pueden configurar para usuarios con rol CAJERA"
                );

        verify(usuarioRepositorio, never())
                .save(any());
    }

    @Test
    @DisplayName("Actualizar permisos de una CAJERA los guarda correctamente")
    void actualizarPermisos_usuarioEsCajera_actualizaCorrectamente() {

        when(usuarioRepositorio.findById(1L))
                .thenReturn(Optional.of(usuarioCajera));

        when(usuarioRepositorio.save(usuarioCajera))
                .thenReturn(usuarioCajera);

        when(usuarioMapper.aDTO(usuarioCajera))
                .thenReturn(
                        new UsuarioRespuestaDTO(
                                1L,
                                "María López",
                                "maria.lopez",
                                "maria@taller.com",
                                RolUsuario.CAJERA,
                                true,
                                true,
                                false,
                                true,
                                false,
                                true,
                                null
                        )
                );

        /*
         * PermisosActualizarDTO ahora requiere 6 booleanos.
         */
        PermisosActualizarDTO nuevosPermisos =
                new PermisosActualizarDTO(
                        true,
                        true,
                        false,
                        true,
                        false,
                        false
                );

        usuarioServicio.actualizarPermisos(1L, nuevosPermisos);

        assertThat(usuarioCajera.isPuedeAplicarDescuento())
                .isTrue();

        assertThat(usuarioCajera.isPuedeAnularVenta())
                .isTrue();

        assertThat(usuarioCajera.isPuedeCerrarCaja())
                .isFalse();

        verify(usuarioRepositorio)
                .save(usuarioCajera);
    }

    @Test
    @DisplayName("Obtener usuario inexistente lanza RecursoNoEncontradoExcepcion")
    void obtenerPorId_usuarioNoExiste_lanzaExcepcion() {

        when(usuarioRepositorio.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> usuarioServicio.obtenerPorId(99L)
        )
                .isInstanceOf(RecursoNoEncontradoExcepcion.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Desactivar usuario cambia estaActivo a false")
    void desactivar_usuarioExistente_loDesactiva() {

        when(usuarioRepositorio.findById(1L))
                .thenReturn(Optional.of(usuarioCajera));

        when(usuarioRepositorio.save(usuarioCajera))
                .thenReturn(usuarioCajera);

        usuarioServicio.desactivar(1L);

        assertThat(usuarioCajera.isEstaActivo())
                .isFalse();

        verify(usuarioRepositorio)
                .save(usuarioCajera);
    }

    @Test
    @DisplayName("Listar todos excluye usuarios eliminados lógicamente")
    void listarTodos_excluyeEliminados() {

        Usuario eliminado = Usuario.builder()
                .nombreCompleto("Usuario Eliminado")
                .nombreUsuario("eliminado")
                .rol(RolUsuario.MECANICO)
                .build();

        eliminado.setEliminadoEn(
                java.time.OffsetDateTime.now()
        );

        when(usuarioRepositorio.findAll())
                .thenReturn(List.of(usuarioCajera, eliminado));

        when(usuarioMapper.aDTO(usuarioCajera))
                .thenReturn(
                        new UsuarioRespuestaDTO(
                                1L,
                                "María López",
                                "maria.lopez",
                                "maria@taller.com",
                                RolUsuario.CAJERA,
                                true,
                                false,
                                true,
                                false,
                                false,
                                true,
                                null
                        )
                );

        List<UsuarioRespuestaDTO> resultado =
                usuarioServicio.listarTodos();

        assertThat(resultado)
                .hasSize(1);

        assertThat(resultado.get(0).nombreUsuario())
                .isEqualTo("maria.lopez");
    }
}