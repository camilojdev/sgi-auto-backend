// ConfiguracionNegocioActualizarDTO.java
package com.sgi.auto.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfiguracionNegocioActualizarDTO(
        @NotBlank(message = "El nombre del negocio es obligatorio") @Size(max = 150) String nombreNegocio,
        @Size(max = 200) String eslogan,
        @Size(max = 300) String direccion,
        @Size(max = 20) String telefono,
        @Size(max = 20) String nit,
        @Size(max = 500) String piePaginaFactura
) {}