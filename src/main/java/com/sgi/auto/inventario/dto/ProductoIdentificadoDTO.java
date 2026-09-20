package com.sgi.auto.inventario.dto;

public record ProductoIdentificadoDTO(
        ProductoRespuestaDTO producto,
        boolean encontradoPorCodigoAnterior
) {}