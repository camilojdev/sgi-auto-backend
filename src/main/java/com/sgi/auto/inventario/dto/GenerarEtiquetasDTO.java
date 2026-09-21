package com.sgi.auto.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerarEtiquetasDTO(
        @NotBlank(message = "Debes indicar la plantilla a usar")
        String plantillaCodigo,

        @NotEmpty(message = "La cola de impresión no puede estar vacía")
        @Valid
        List<ItemEtiquetaDTO> items
) {
    public record ItemEtiquetaDTO(
            @NotNull Long productoId,
            @Min(value = 1, message = "La cantidad debe ser al menos 1") int cantidad
    ) {}
}