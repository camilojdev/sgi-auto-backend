package com.sgi.auto.usuarios.dto;

import jakarta.validation.constraints.*;

public record SolicitarCreacionDuenoDTO(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150)
        String nombreCompleto,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(max = 60)
        String nombreUsuario,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Debe ser un correo válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
                message = "La contraseña debe tener mínimo 8 caracteres, una mayúscula y un número")
        String contrasena
) {}