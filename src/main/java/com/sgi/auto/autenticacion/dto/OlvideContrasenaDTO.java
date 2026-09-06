package com.sgi.auto.autenticacion.dto;

import jakarta.validation.constraints.NotBlank;

public record OlvideContrasenaDTO(
        @NotBlank(message = "Ingresa tu usuario o correo")
        String identificador
) {}