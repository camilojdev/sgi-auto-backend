package com.sgi.auto.taller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenDeTrabajoRepositorio extends JpaRepository<OrdenDeTrabajo, Long> {

    // Historial por placa
    @Query("SELECT o FROM OrdenDeTrabajo o WHERE o.placa = :placa ORDER BY o.creadoEn DESC")
    List<OrdenDeTrabajo> buscarPorPlaca(@Param("placa") String placa);

    // OTs del día para el mecánico (RF-100)
    @Query("""
            SELECT o FROM OrdenDeTrabajo o
            WHERE o.mecanico.id = :mecanicoId
              AND o.estado NOT IN ('ENTREGADO','CANCELADO')
            ORDER BY o.creadoEn ASC
            """)
    List<OrdenDeTrabajo> otActivasPorMecanico(@Param("mecanicoId") Long mecanicoId);

    // Todas las OTs activas
    @Query("SELECT o FROM OrdenDeTrabajo o WHERE o.estado NOT IN ('ENTREGADO','CANCELADO') ORDER BY o.creadoEn DESC")
    Page<OrdenDeTrabajo> listarActivas(Pageable pageable);

    // Todas las OTs
    @Query("SELECT o FROM OrdenDeTrabajo o ORDER BY o.creadoEn DESC")
    Page<OrdenDeTrabajo> listarTodas(Pageable pageable);

    // Búsqueda por el código seguro impreso en el código de barras
    Optional<OrdenDeTrabajo> findByCodigoSeguro(String codigoSeguro);

    boolean existsByCodigoSeguro(String codigoSeguro);
}