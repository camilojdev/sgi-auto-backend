package com.sgi.auto.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductoCrearDTO(

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 50)
        String codigo,

        @Size(max = 20, message = "El número interno no puede superar los 20 caracteres")
        String numeroInterno,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200)
        String nombre,

        String descripcion,

        Long categoriaId,
        Long proveedorId,

        @Size(max = 30)
        String unidadMedida,

        @NotNull(message = "El precio de compra es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal precioCompraConIva,

        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal precioVentaCop,

        @Min(value = 0)
        int stockActual,

        @Min(value = 0)
        int stockMinimo,

        boolean mostrarEnListaPrecios,

        @Size(max = 20, message = "El precio oculto no puede superar los 20 caracteres")
        String precioOculto
) {}