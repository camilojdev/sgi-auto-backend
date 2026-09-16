// ActualizarNombreDTO.java
package com.sgi.auto.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarNombreDTO(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombreCompleto
) {}