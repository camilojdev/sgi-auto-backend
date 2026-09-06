package com.sgi.auto.autenticacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RestablecerContrasenaDTO(
        @NotBlank(message = "Ingresa tu usuario o correo")
        String identificador,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener 6 dígitos")
        String codigo,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
                message = "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número"
        )
        String nuevaContrasena
) {}