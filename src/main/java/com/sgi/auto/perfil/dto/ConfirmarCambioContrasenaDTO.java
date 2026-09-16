// ConfirmarCambioContrasenaDTO.java
package com.sgi.auto.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmarCambioContrasenaDTO(
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener 6 dígitos")
        String codigo,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
                message = "La nueva contraseña debe tener mínimo 8 caracteres, una mayúscula y un número")
        String contrasenaNueva
) {}