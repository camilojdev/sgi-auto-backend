package com.sgi.auto.compartido;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

 // Envía correos usando la API HTTP de Brevo (https://api.brevo.com/v3/smtp/email)
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServicio {

    private final RestTemplate restTemplate;

    @Value("${sgi.brevo.api-key}")
    private String apiKey;

    @Value("${sgi.brevo.remitente-nombre}")
    private String remitenteNombre;

    @Value("${sgi.brevo.remitente-correo}")
    private String remitenteCorreo;

    private static final String URL_BREVO = "https://api.brevo.com/v3/smtp/email";

    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);
        headers.set("accept", "application/json");

        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of("name", remitenteNombre, "email", remitenteCorreo),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "htmlContent", cuerpoHtml
        );

        try {
            restTemplate.postForEntity(URL_BREVO, new HttpEntity<>(cuerpo, headers), String.class);
            log.info("Correo enviado a {}: {}", destinatario, asunto);
        } catch (HttpClientErrorException e) {
            log.error("Brevo rechazó el correo a {}: {} - {}",
                    destinatario, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("No se pudo enviar el correo: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", destinatario, e.getMessage(), e);
            throw new RuntimeException("No se pudo enviar el correo: " + e.getMessage());
        }
    }
}