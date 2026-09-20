package com.sgi.auto.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductoCodigoRepositorio extends JpaRepository<ProductoCodigo, Long> {

    List<ProductoCodigo> findByProductoIdOrderByFechaCreacionDesc(Long productoId);

    Optional<ProductoCodigo> findByProductoIdAndActivoTrue(Long productoId);

    Optional<ProductoCodigo> findFirstByCodigoOrderByFechaCreacionDesc(String codigo);

    boolean existsByCodigoAndProductoIdNot(String codigo, Long productoId);
}