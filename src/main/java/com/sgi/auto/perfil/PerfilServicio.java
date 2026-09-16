package com.sgi.auto.perfil;

import com.sgi.auto.autenticacion.CodigoRecuperacion;
import com.sgi.auto.autenticacion.CodigoVerificacionServicio;
import com.sgi.auto.autenticacion.TipoCodigoRecuperacion;
import com.sgi.auto.compartido.ConflictoExcepcion;
import com.sgi.auto.compartido.ImagenServicio;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.perfil.dto.*;
import com.sgi.auto.usuarios.Usuario;
import com.sgi.auto.usuarios.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfilServicio {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final ImagenServicio imagenServicio;
    private final CodigoVerificacionServicio codigoVerificacionServicio;

    @Transactional(readOnly = true)
    public PerfilRespuestaDTO obtener(Long usuarioId) {
        return aDTO(buscarOLanzar(usuarioId));
    }

    @Transactional
    public PerfilRespuestaDTO actualizarNombre(Long usuarioId, ActualizarNombreDTO dto) {
        Usuario usuario = buscarOLanzar(usuarioId);
        usuario.setNombreCompleto(dto.nombreCompleto());
        return aDTO(usuarioRepositorio.save(usuario));
    }

    // ── Foto de perfil ────────────────────────────────────────

    @Transactional
    public PerfilRespuestaDTO actualizarFoto(Long usuarioId, MultipartFile archivo) {
        Usuario usuario = buscarOLanzar(usuarioId);
        if (usuario.getFotoPublicId() != null) {
            imagenServicio.eliminar(usuario.getFotoPublicId());
        }
        var resultado = imagenServicio.subir(archivo, "sgi-auto/usuarios/" + usuario.getId());
        usuario.setFotoUrl(resultado.url());
        usuario.setFotoPublicId(resultado.publicId());
        return aDTO(usuarioRepositorio.save(usuario));
    }

    @Transactional
    public PerfilRespuestaDTO eliminarFoto(Long usuarioId) {
        Usuario usuario = buscarOLanzar(usuarioId);
        if (usuario.getFotoPublicId() != null) {
            imagenServicio.eliminar(usuario.getFotoPublicId());
        }
        usuario.setFotoUrl(null);
        usuario.setFotoPublicId(null);
        return aDTO(usuarioRepositorio.save(usuario));
    }

    // ── Contraseña — camino A: con contraseña actual ─────────

    @Transactional
    public void cambiarContrasenaConActual(Long usuarioId, CambiarContrasenaConActualDTO dto) {
        Usuario usuario = buscarOLanzar(usuarioId);
        if (!passwordEncoder.matches(dto.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new ReglaNegocioExcepcion("La contraseña actual es incorrecta");
        }
        usuario.setContrasenaHash(passwordEncoder.encode(dto.contrasenaNueva()));
        usuarioRepositorio.save(usuario);
        log.info("Contraseña actualizada desde perfil (con contraseña actual): usuario={}", usuario.getNombreUsuario());
    }

    // ── Contraseña — camino B: con código al correo ──────────

    @Transactional
    public void solicitarCodigoCambioContrasena(Long usuarioId) {
        Usuario usuario = buscarOLanzar(usuarioId);
        codigoVerificacionServicio.generarYEnviar(
                usuario,
                TipoCodigoRecuperacion.CAMBIO_CONTRASENA,
                usuario.getCorreo(),
                null,
                "Código para cambiar tu contraseña",
                """
                <p>Hola %s,</p>
                <p>Tu código para cambiar tu contraseña es:</p>
                <h2>%s</h2>
                <p>Este código es válido por %d minutos. Si no solicitaste este código, ignora este correo.</p>
                """
        );
    }

    @Transactional
    public void confirmarCambioContrasenaConCodigo(Long usuarioId, ConfirmarCambioContrasenaDTO dto) {
        Usuario usuario = buscarOLanzar(usuarioId);
        codigoVerificacionServicio.validarYConsumir(
                usuario.getId(), dto.codigo(), TipoCodigoRecuperacion.CAMBIO_CONTRASENA);
        usuario.setContrasenaHash(passwordEncoder.encode(dto.contrasenaNueva()));
        usuarioRepositorio.save(usuario);
        log.info("Contraseña actualizada desde perfil (con código): usuario={}", usuario.getNombreUsuario());
    }

    // ── Correo — siempre requiere código al correo NUEVO ─────

    @Transactional
    public void solicitarCambioCorreo(Long usuarioId, SolicitarCambioCorreoDTO dto) {
        Usuario usuario = buscarOLanzar(usuarioId);
        String correoNuevo = dto.correoNuevo().trim().toLowerCase();

        if (correoNuevo.equalsIgnoreCase(usuario.getCorreo())) {
            throw new ReglaNegocioExcepcion("Ese ya es tu correo actual");
        }
        if (usuarioRepositorio.buscarPorCorreo(correoNuevo).isPresent()) {
            throw new ConflictoExcepcion("Ese correo ya está en uso por otro usuario");
        }

        codigoVerificacionServicio.generarYEnviar(
                usuario,
                TipoCodigoRecuperacion.CAMBIO_CORREO,
                correoNuevo,
                correoNuevo,
                "Confirma tu nuevo correo",
                """
                <p>Hola %s,</p>
                <p>Tu código para confirmar este correo como tu nuevo correo de acceso es:</p>
                <h2>%s</h2>
                <p>Este código es válido por %d minutos. Si no solicitaste este cambio, ignora este correo.</p>
                """
        );
    }

    @Transactional
    public PerfilRespuestaDTO confirmarCambioCorreo(Long usuarioId, ConfirmarCambioCorreoDTO dto) {
        Usuario usuario = buscarOLanzar(usuarioId);
        CodigoRecuperacion codigoValido = codigoVerificacionServicio.validarYConsumir(
                usuario.getId(), dto.codigo(), TipoCodigoRecuperacion.CAMBIO_CORREO);

        // Doble chequeo por si alguien más tomó el correo mientras el código estaba pendiente de confirmar.
        if (usuarioRepositorio.buscarPorCorreo(codigoValido.getCorreoDestino()).isPresent()) {
            throw new ConflictoExcepcion("Ese correo ya está en uso por otro usuario");
        }

        usuario.setCorreo(codigoValido.getCorreoDestino());
        Usuario actualizado = usuarioRepositorio.save(usuario);
        log.info("Correo actualizado desde perfil: usuario={}, correoNuevo={}",
                usuario.getNombreUsuario(), codigoValido.getCorreoDestino());
        return aDTO(actualizado);
    }

    private Usuario buscarOLanzar(Long id) {
        return usuarioRepositorio.findById(id)
                .filter(u -> !u.estaEliminado())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion("No se encontró el usuario con id: " + id));
    }

    private PerfilRespuestaDTO aDTO(Usuario u) {
        return new PerfilRespuestaDTO(
                u.getId(), u.getNombreCompleto(), u.getNombreUsuario(),
                u.getCorreo(), u.getRol().name(), u.getFotoUrl());
    }
}