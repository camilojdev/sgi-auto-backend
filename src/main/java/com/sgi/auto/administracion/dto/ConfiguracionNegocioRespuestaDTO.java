package com.sgi.auto.administracion.dto;

public record ConfiguracionNegocioRespuestaDTO(
        String nombreNegocio, String eslogan, String logoUrl,
        String logoEtiquetasUrl,
        String direccion, String telefono, String nit, String piePaginaFactura
) {}