package com.sgi.auto.inventario.dto;

import java.time.OffsetDateTime;

public record ProductoCodigoRespuestaDTO(
        Long id,
        String codigo,
        boolean activo,
        OffsetDateTime fechaCreacion,
        String cambiadoPorNombre
) {}