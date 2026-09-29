package com.sgi.auto.pos.dto;

import com.sgi.auto.pos.EstadoVenta;
import com.sgi.auto.pos.MetodoPago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record VentaRespuestaDTO(
        Long id,
        String numeroVenta,
        String codigoSeguro,
        String claveIdempotencia,
        String nombreCliente,
        MetodoPago metodoPago,
        EstadoVenta estado,
        BigDecimal subtotalCop,
        BigDecimal descuentoCop,
        BigDecimal totalCop,
        BigDecimal montoPagadoCop,
        BigDecimal vueltoCop,
        String razonAnulacion,
        int puntosGanados,
        List<ItemVentaRespuestaDTO> items,
        OffsetDateTime creadoEn
) {
    public record ItemVentaRespuestaDTO(
            Long productoId,
            String nombreProducto,
            String codigoProducto,
            int cantidad,
            BigDecimal precioUnitarioCop,
            BigDecimal subtotalCop
    ) {}
}