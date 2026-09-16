// CambiarContrasenaConActualDTO.java
package com.sgi.auto.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarContrasenaConActualDTO(
        @NotBlank(message = "Debes ingresar tu contraseña actual")
        String contrasenaActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
                message = "La nueva contraseña debe tener mínimo 8 caracteres, una mayúscula y un número")
        String contrasenaNueva
) {}