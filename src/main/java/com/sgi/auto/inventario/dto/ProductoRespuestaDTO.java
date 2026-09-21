package com.sgi.auto.inventario.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductoRespuestaDTO(
        Long id,
        String codigo,
        String numeroInterno,
        String nombre,
        String descripcion,
        Long categoriaId,
        String categoriaNombre,
        String proveedorNombre,
        String unidadMedida,
        BigDecimal precioCompraConIva,
        BigDecimal precioCompraSinIva,
        BigDecimal precioVentaDetal,
        BigDecimal precioVentaMayor,
        BigDecimal margenGananciaPct,
        int stockActual,
        int stockMinimo,
        boolean stockBajoMinimo,
        boolean mostrarEnListaPrecios,
        boolean estaActivo,
        OffsetDateTime creadoEn,
        String precioOculto
) {}