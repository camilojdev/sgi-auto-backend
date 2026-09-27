package com.sgi.auto.taller.dto;

import com.sgi.auto.pos.MetodoPago;
import com.sgi.auto.taller.EstadoOT;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OTRespuestaDTO(
        Long id,
        String numeroOt,
        String codigoSeguro,
        String nombreCliente,
        String celularCliente,
        String placa,
        String marcaVehiculo,
        String modeloVehiculo,
        Integer anioVehiculo,
        String colorVehiculo,
        Integer kilometraje,
        String descripcionProblema,
        String observacionesMecanico,
        EstadoOT estado,
        String mecanicoNombre,
        MetodoPago metodoPago,
        BigDecimal totalServiciosCop,
        BigDecimal totalRepuestosCop,
        BigDecimal descuentoCop,
        BigDecimal granTotalCop,
        List<ServicioRespuestaDTO> servicios,
        List<RepuestoRespuestaDTO> repuestos,
        OffsetDateTime fechaPrometidaEntrega,
        OffsetDateTime fechaEntregaReal,
        OffsetDateTime creadoEn
) {
    public record ServicioRespuestaDTO(
            Long id,
            String descripcion,
            int cantidad,
            BigDecimal precioUnitarioCop,
            BigDecimal subtotalCop
    ) {}

    public record RepuestoRespuestaDTO(
            Long id,
            String nombreRepuesto,
            int cantidad,
            BigDecimal precioUnitarioCop,
            BigDecimal subtotalCop,
            boolean stockDescontado
    ) {}
}