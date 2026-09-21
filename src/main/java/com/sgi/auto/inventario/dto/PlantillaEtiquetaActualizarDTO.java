package com.sgi.auto.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlantillaEtiquetaActualizarDTO(
        @NotNull @DecimalMin(value = "0.1") BigDecimal anchoMm,
        @NotNull @DecimalMin(value = "0.1") BigDecimal altoMm,
        @Min(1) int columnas,
        @Min(1) int filas,
        @NotNull BigDecimal margenSuperiorMm,
        @NotNull BigDecimal margenIzquierdoMm,
        @NotNull BigDecimal separacionHorizontalMm,
        @NotNull BigDecimal separacionVerticalMm
) {}