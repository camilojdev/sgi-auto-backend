package com.sgi.auto.usuarios;

import com.sgi.auto.compartido.ConflictoExcepcion;
import com.sgi.auto.compartido.EmailServicio;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.usuarios.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.Duration;

import java.security.SecureRandom;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder codificadorContrasena;
    private final SolicitudCreacionDuenoRepositorio solicitudCreacionDuenoRepositorio;
    private final EmailServicio emailServicio;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_INTENTOS_CODIGO_DUENO = 5;

    @Transactional
    public UsuarioRespuestaDTO crear(UsuarioCrearDTO solicitud) {
        if (solicitud.rol() == RolUsuario.DUENO) {
            throw new ReglaNegocioExcepcion("Crear un usuario con rol DUENO requiere verificación por correo.");
        }

        if (usuarioRepositorio.existePorNombreUsuario(solicitud.nombreUsuario())) {
            throw new ConflictoExcepcion(
                    "Ya existe un usuario con el nombre de usuario: " + solicitud.nombreUsuario());
        }

        Usuario usuario = usuarioMapper.aEntidad(solicitud);
        usuario.setContrasenaHash(codificadorContrasena.encode(solicitud.contrasena()));

        // los permisos granulares solo aplican a CAJERA
        if (usuario.getRol() != RolUsuario.CAJERA) {
            usuario.setPuedeAplicarDescuento(false);
            usuario.setPuedeAnularVenta(false);
            usuario.setPuedeCerrarCaja(false);
            usuario.setPuedeVerReportes(false);
            usuario.setPuedeGestionarCredito(false);
        }

        Usuario guardado = usuarioRepositorio.save(usuario);
        log.info("Usuario creado: nombreUsuario={}, rol={}",
                guardado.getNombreUsuario(), guardado.getRol());

        return usuarioMapper.aDTO(guardado);
    }

    @Transactional
    public void cambiarContrasena(Long id, CambiarContrasenaDTO solicitud) {
        Usuario usuario = buscarOLanzar(id);
        if (!codificadorContrasena.matches(solicitud.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new ReglaNegocioExcepcion("La contraseña actual es incorrecta");
        }
        usuario.setContrasenaHash(codificadorContrasena.encode(solicitud.contrasenaNueva()));
        usuarioRepositorio.save(usuario);
        log.info("Contraseña actualizada: nombreUsuario={}", usuario.getNombreUsuario());
    }

    // Lista todos los usuarios activos (no eliminados).
    @Transactional(readOnly = true)
    public List<UsuarioRespuestaDTO> listarTodos() {
        return usuarioRepositorio.findAll().stream()
                .filter(u -> !u.estaEliminado())
                .map(usuarioMapper::aDTO)
                .toList();
    }

    // Obtiene un usuario por su id.
    @Transactional(readOnly = true)
    public UsuarioRespuestaDTO obtenerPorId(Long id) {
        Usuario usuario = buscarOLanzar(id);
        return usuarioMapper.aDTO(usuario);
    }

    // Actualiza los permisos granulares de un usuario CAJERA.
    @Transactional
    public UsuarioRespuestaDTO actualizarPermisos(Long id, PermisosActualizarDTO permisos) {
        Usuario usuario = buscarOLanzar(id);

        if (usuario.getRol() != RolUsuario.CAJERA) {
            throw new com.sgi.auto.compartido.ReglaNegocioExcepcion(
                    "Los permisos granulares solo se pueden configurar para usuarios con rol CAJERA");
        }

        usuario.setPuedeAplicarDescuento(permisos.puedeAplicarDescuento());
        usuario.setPuedeAnularVenta(permisos.puedeAnularVenta());
        usuario.setPuedeCerrarCaja(permisos.puedeCerrarCaja());
        usuario.setPuedeVerReportes(permisos.puedeVerReportes());
        usuario.setPuedeGestionarCredito(permisos.puedeGestionarCredito());

        Usuario actualizado = usuarioRepositorio.save(usuario);
        log.info("Permisos actualizados para usuario: nombreUsuario={}", actualizado.getNombreUsuario());

        return usuarioMapper.aDTO(actualizado);
    }

    // Desactiva un usuario (soft delete lógico vía estaActivo, no elimina el registro).
    @Transactional
    public void desactivar(Long id) {
        Usuario usuario = buscarOLanzar(id);
        usuario.setEstaActivo(false);
        usuarioRepositorio.save(usuario);
        log.info("Usuario desactivado: nombreUsuario={}", usuario.getNombreUsuario());
    }

    // Reactiva un usuario previamente desactivado.
    @Transactional
    public UsuarioRespuestaDTO reactivar(Long id) {
        Usuario usuario = buscarOLanzar(id);
        usuario.setEstaActivo(true);
        Usuario actualizado = usuarioRepositorio.save(usuario);
        return usuarioMapper.aDTO(actualizado);
    }

    @Transactional
    public void solicitarCreacionDueno(Long solicitanteId, SolicitarCreacionDuenoDTO dto) {
        Usuario solicitante = buscarOLanzar(solicitanteId);

        if (usuarioRepositorio.existePorNombreUsuario(dto.nombreUsuario())) {
            throw new ConflictoExcepcion(
                    "Ya existe un usuario con el nombre de usuario: " + dto.nombreUsuario());
        }
        if (usuarioRepositorio.buscarPorCorreo(dto.correo()).isPresent()) {
            throw new ConflictoExcepcion("Ya existe un usuario con ese correo");
        }

        solicitudCreacionDuenoRepositorio
                .findFirstBySolicitanteIdOrderByCreadoEnDesc(solicitanteId)
                .ifPresent(ultima -> {
                    long segundos = Duration.between(
                            ultima.getCreadoEn(), java.time.OffsetDateTime.now()).getSeconds();
                    if (segundos < 60) {
                        throw new ReglaNegocioExcepcion(
                                "Ya se envió un código recientemente. Espera un momento antes de pedir otro.");
                    }
                });

        String codigo = String.valueOf(100000 + RANDOM.nextInt(900000));

        SolicitudCreacionDueno solicitud = SolicitudCreacionDueno.builder()
                .solicitanteId(solicitanteId)
                .nombreCompleto(dto.nombreCompleto())
                .nombreUsuario(dto.nombreUsuario())
                .correo(dto.correo())
                .contrasenaHash(codificadorContrasena.encode(dto.contrasena()))
                .codigo(codigo)
                .expiraEn(OffsetDateTime.now().plusMinutes(15))
                .build();
        solicitudCreacionDuenoRepositorio.save(solicitud);

        String cuerpo = """
                <p>Hola %s,</p>
                <p>Solicitaste crear un nuevo usuario con rol <b>DUEÑO</b>:</p>
                <ul>
                  <li>Nombre: %s</li>
                  <li>Usuario: %s</li>
                  <li>Correo: %s</li>
                </ul>
                <p>Si fuiste tú, confirma con este código:</p>
                <h2>%s</h2>
                <p>Este código es válido por 15 minutos. Si no solicitaste esto, ignora este correo
                y considera cambiar tu contraseña de inmediato.</p>
                """.formatted(solicitante.getNombreCompleto(), dto.nombreCompleto(),
                dto.nombreUsuario(), dto.correo(), codigo);

        emailServicio.enviar(solicitante.getCorreo(),
                "Confirma la creación de un nuevo usuario DUEÑO", cuerpo);

        log.info("Solicitud de creación de DUEÑO generada por usuario id={}", solicitanteId);
    }

    @Transactional
    public UsuarioRespuestaDTO confirmarCreacionDueno(Long solicitanteId, ConfirmarCreacionDuenoDTO dto) {
        SolicitudCreacionDueno solicitud = solicitudCreacionDuenoRepositorio
                .findFirstBySolicitanteIdAndUsadaFalseAndExpiraEnAfterOrderByCreadoEnDesc(
                        solicitanteId, java.time.OffsetDateTime.now())
                .orElseThrow(() -> new ReglaNegocioExcepcion("Código inválido o expirado"));

        if (!solicitud.getCodigo().equals(dto.codigo())) {
            solicitud.setIntentosFallidos(solicitud.getIntentosFallidos() + 1);

            if (solicitud.getIntentosFallidos() >= MAX_INTENTOS_CODIGO_DUENO) {
                solicitud.setUsada(true);
                solicitudCreacionDuenoRepositorio.save(solicitud);
                throw new ReglaNegocioExcepcion("Demasiados intentos fallidos. Solicita un nuevo código.");
            }

            solicitudCreacionDuenoRepositorio.save(solicitud);
            int restantes = MAX_INTENTOS_CODIGO_DUENO - solicitud.getIntentosFallidos();
            throw new ReglaNegocioExcepcion("Código incorrecto. Te quedan " + restantes + " intento(s).");
        }

        // Doble chequeo por si cambió algo mientras el código estaba pendiente
        if (usuarioRepositorio.existePorNombreUsuario(solicitud.getNombreUsuario())) {
            throw new ConflictoExcepcion("Ya existe un usuario con ese nombre de usuario");
        }
        if (usuarioRepositorio.buscarPorCorreo(solicitud.getCorreo()).isPresent()) {
            throw new ConflictoExcepcion("Ya existe un usuario con ese correo");
        }

        Usuario nuevo = Usuario.builder()
                .nombreCompleto(solicitud.getNombreCompleto())
                .nombreUsuario(solicitud.getNombreUsuario())
                .correo(solicitud.getCorreo())
                .contrasenaHash(solicitud.getContrasenaHash())
                .rol(RolUsuario.DUENO)
                .build();

        Usuario guardado = usuarioRepositorio.save(nuevo);

        solicitud.setUsada(true);
        solicitudCreacionDuenoRepositorio.save(solicitud);

        log.info("Usuario DUEÑO creado tras verificación: nombreUsuario={}, solicitadoPor={}",
                guardado.getNombreUsuario(), solicitanteId);

        return usuarioMapper.aDTO(guardado);
    }

    // ── Helper privado ──────────────────────────────────────────

    private Usuario buscarOLanzar(Long id) {
        return usuarioRepositorio.findById(id)
                .filter(u -> !u.estaEliminado())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró el usuario con id: " + id));
    }
}
