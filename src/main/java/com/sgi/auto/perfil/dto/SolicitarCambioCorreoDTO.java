// SolicitarCambioCorreoDTO.java
package com.sgi.auto.perfil.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarCambioCorreoDTO(
        @NotBlank(message = "El correo nuevo es obligatorio")
        @Email(message = "Debe ser un correo válido")
        String correoNuevo
) {}