package com.sgi.auto.compartido;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Servicio genérico de envío de correos. Pensado para reutilizarse en
 * distintos casos: recuperación de contraseña, facturas, reportes de
 * crédito, etc. — cualquier necesidad futura de enviar correo desde
 * la app pasa por aquí.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServicio {

    private final JavaMailSender mailSender;

    private static final String REMITENTE_NOMBRE = "Almacén y Servicios Eléctricos DB";

    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            helper.setFrom("almacenyservicioselectricosdb@gmail.com", REMITENTE_NOMBRE);
            mailSender.send(mensaje);
            log.info("Correo enviado a {}: {}", destinatario, asunto);
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage(), e);
            throw new RuntimeException("No se pudo enviar el correo: " + e.getMessage());
        }
    }
}