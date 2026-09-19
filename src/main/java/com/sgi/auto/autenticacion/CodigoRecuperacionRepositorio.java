package com.sgi.auto.autenticacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CodigoRecuperacionRepositorio extends JpaRepository<CodigoRecuperacion, Long> {

    Optional<CodigoRecuperacion> findFirstByUsuarioIdAndTipoOrderByCreadoEnDesc(
            Long usuarioId, TipoCodigoRecuperacion tipo);

    Optional<CodigoRecuperacion> findFirstByUsuarioIdAndTipoAndUsadoFalseAndExpiraEnAfterOrderByCreadoEnDesc(
            Long usuarioId, TipoCodigoRecuperacion tipo, OffsetDateTime ahora);
}