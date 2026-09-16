// PerfilRespuestaDTO.java
package com.sgi.auto.perfil.dto;

public record PerfilRespuestaDTO(
        Long id,
        String nombreCompleto,
        String nombreUsuario,
        String correo,
        String rol,
        String fotoUrl
) {}