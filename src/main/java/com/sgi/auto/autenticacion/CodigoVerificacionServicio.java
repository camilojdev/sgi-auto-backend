package com.sgi.auto.autenticacion;

import com.sgi.auto.compartido.EmailServicio;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.usuarios.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodigoVerificacionServicio {

    private final CodigoRecuperacionRepositorio codigoRepositorio;
    private final EmailServicio emailServicio;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MINUTOS_VALIDEZ_CODIGO = 15;
    private static final int SEGUNDOS_ENTRE_SOLICITUDES = 60;
    private static final int MAX_INTENTOS = 5;

    /*
     * Genera un código de 6 dígitos, lo guarda y lo envía por correo.
     * El cooldown de 60s es por tipo — pedir un código de cambio de correo
     * no bloquea pedir uno de cambio de contraseña, y viceversa.
     *
     * correoEnvio: a qué dirección se envía
     * correoDestino solo aplica para CAMBIO_CORREO:
     */
    public void generarYEnviar(Usuario usuario, TipoCodigoRecuperacion tipo,
                               String correoEnvio, String correoDestino,
                               String asunto, String plantillaMensaje) {

        codigoRepositorio.findFirstByUsuarioIdAndTipoOrderByCreadoEnDesc(usuario.getId(), tipo)
                .ifPresent(ultimo -> {
                    long segundos = Duration.between(ultimo.getCreadoEn(), OffsetDateTime.now()).getSeconds();
                    if (segundos < SEGUNDOS_ENTRE_SOLICITUDES) {
                        throw new ReglaNegocioExcepcion(
                                "Ya se envió un código recientemente. Espera un momento antes de pedir otro.");
                    }
                });

        String codigo = generarCodigo();

        CodigoRecuperacion nuevo = CodigoRecuperacion.builder()
                .usuario(usuario)
                .codigo(codigo)
                .tipo(tipo)
                .correoDestino(correoDestino)
                .expiraEn(OffsetDateTime.now().plusMinutes(MINUTOS_VALIDEZ_CODIGO))
                .build();
        codigoRepositorio.save(nuevo);

        String cuerpo = plantillaMensaje.formatted(usuario.getNombreCompleto(), codigo, MINUTOS_VALIDEZ_CODIGO);
        emailServicio.enviar(correoEnvio, asunto, cuerpo);

        log.info("Código {} generado para usuario id={}", tipo, usuario.getId());
    }

    public CodigoRecuperacion validarYConsumir(Long usuarioId, String codigo, TipoCodigoRecuperacion tipo) {
        CodigoRecuperacion pendiente = codigoRepositorio
                .findFirstByUsuarioIdAndTipoAndUsadoFalseAndExpiraEnAfterOrderByCreadoEnDesc(
                        usuarioId, tipo, OffsetDateTime.now())
                .orElseThrow(() -> new ReglaNegocioExcepcion("Código inválido o expirado"));

        if (!pendiente.getCodigo().equals(codigo)) {
            pendiente.setIntentosFallidos(pendiente.getIntentosFallidos() + 1);

            if (pendiente.getIntentosFallidos() >= MAX_INTENTOS) {
                pendiente.setUsado(true);
                codigoRepositorio.save(pendiente);
                throw new ReglaNegocioExcepcion(
                        "Demasiados intentos fallidos. Solicita un nuevo código.");
            }

            codigoRepositorio.save(pendiente);
            int restantes = MAX_INTENTOS - pendiente.getIntentosFallidos();
            throw new ReglaNegocioExcepcion(
                    "Código incorrecto. Te quedan " + restantes + " intento(s).");
        }

        pendiente.setUsado(true);
        codigoRepositorio.save(pendiente);
        return pendiente;
    }

    private String generarCodigo() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}