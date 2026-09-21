package com.sgi.auto.inventario.dto;

import java.math.BigDecimal;

public record PlantillaEtiquetaRespuestaDTO(
        String codigo,
        String nombre,
        String tipo,
        BigDecimal anchoMm,
        BigDecimal altoMm,
        int columnas,
        int filas,
        BigDecimal margenSuperiorMm,
        BigDecimal margenIzquierdoMm,
        BigDecimal separacionHorizontalMm,
        BigDecimal separacionVerticalMm
) {}