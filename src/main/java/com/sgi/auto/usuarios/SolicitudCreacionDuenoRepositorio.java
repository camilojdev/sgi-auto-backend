package com.sgi.auto.usuarios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SolicitudCreacionDuenoRepositorio extends JpaRepository<SolicitudCreacionDueno, Long> {

    Optional<SolicitudCreacionDueno> findFirstBySolicitanteIdOrderByCreadoEnDesc(Long solicitanteId);

    @Query("""
        SELECT s FROM SolicitudCreacionDueno s
        WHERE s.solicitanteId = :solicitanteId
          AND s.codigo = :codigo
          AND s.usada = false
          AND s.expiraEn > :ahora
        ORDER BY s.creadoEn DESC
        """)
    Optional<SolicitudCreacionDueno> buscarValida(
            @Param("solicitanteId") Long solicitanteId,
            @Param("codigo") String codigo,
            @Param("ahora") OffsetDateTime ahora);

    Optional<SolicitudCreacionDueno> findFirstBySolicitanteIdAndUsadaFalseAndExpiraEnAfterOrderByCreadoEnDesc(
            Long solicitanteId, OffsetDateTime ahora);
}