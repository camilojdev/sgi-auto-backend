package com.sgi.auto.autenticacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CodigoRecuperacionRepositorio extends JpaRepository<CodigoRecuperacion, Long> {

    @Query("""
        SELECT c FROM CodigoRecuperacion c
        WHERE c.usuario.id = :usuarioId
          AND c.codigo = :codigo
          AND c.usado = false
          AND c.expiraEn > :ahora
        ORDER BY c.creadoEn DESC
        """)
    Optional<CodigoRecuperacion> buscarValido(
            @Param("usuarioId") Long usuarioId,
            @Param("codigo") String codigo,
            @Param("ahora") OffsetDateTime ahora);

    Optional<CodigoRecuperacion> findFirstByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}