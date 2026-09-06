package com.sgi.auto.usuarios.dto;

import com.sgi.auto.usuarios.RolUsuario;
import jakarta.validation.constraints.*;

public record UsuarioCrearDTO(

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150)
        String nombreCompleto,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 4, max = 60, message = "El nombre de usuario debe tener entre 4 y 60 caracteres")
        String nombreUsuario,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
                message = "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número"
        )
        String contrasena,

        @NotNull(message = "El rol es obligatorio")
        RolUsuario rol,

        // Permisos granulares — solo aplican si rol = CAJERA
        boolean puedeAplicarDescuento,
        boolean puedeAnularVenta,
        boolean puedeCerrarCaja,
        boolean puedeVerReportes,
        boolean puedeGestionarCredito
) {}