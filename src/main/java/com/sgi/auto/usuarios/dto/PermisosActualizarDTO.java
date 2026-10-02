package com.sgi.auto.usuarios.dto;

public record PermisosActualizarDTO(
        boolean puedeAplicarDescuento,
        boolean puedeAnularVenta,
        boolean puedeCerrarCaja,
        boolean puedeVerReportes,
        boolean puedeGestionarCredito,
        boolean puedeGestionarInventario
) {}
