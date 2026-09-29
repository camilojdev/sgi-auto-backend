package com.sgi.auto.pos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface VentaRepositorio extends JpaRepository<Venta, Long> {

    // Verificar idempotencia
    Optional<Venta> findByClaveIdempotencia(String claveIdempotencia);

    // Historial de ventas de un cliente
    @Query("SELECT v FROM Venta v WHERE v.cliente.id = :clienteId ORDER BY v.creadoEn DESC")
    Page<Venta> ventasPorCliente(@Param("clienteId") Long clienteId, Pageable pageable);

    // Ventas del día para reportes
    @Query("""
        SELECT v FROM Venta v
        WHERE v.estado = 'COMPLETADA'
          AND v.creadoEn >= :inicioDia
        ORDER BY v.creadoEn DESC
        """)
    Page<Venta> ventasDeHoy(@Param("inicioDia") OffsetDateTime inicioDia, Pageable pageable);

    // Búsqueda por el código escaneado del código de barras de la factura
    Optional<Venta> findByCodigoSeguro(String codigoSeguro);

    boolean existsByCodigoSeguro(String codigoSeguro);
}