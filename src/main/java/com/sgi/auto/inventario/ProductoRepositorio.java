package com.sgi.auto.inventario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepositorio extends JpaRepository<Producto, Long> {

    @Query(value = """
        SELECT * FROM productos
        WHERE eliminado_en IS NULL
          AND esta_activo = true
          AND (nombre ILIKE '%' || :termino || '%'
               OR codigo ILIKE '%' || :termino || '%')
        ORDER BY nombre
        LIMIT 20
        """, nativeQuery = true)
    List<Producto> buscarPorNombre(@Param("termino") String termino);

    @Query("SELECT p FROM Producto p WHERE p.codigo = :codigo AND p.estaActivo = true AND p.eliminadoEn IS NULL")
    Optional<Producto> buscarPorCodigo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.codigo = :codigo AND p.eliminadoEn IS NULL")
    boolean existePorCodigo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.codigo = :codigo AND p.id <> :id AND p.eliminadoEn IS NULL")
    boolean existePorCodigoExcluyendoId(@Param("codigo") String codigo, @Param("id") Long id);

    @Query("SELECT p FROM Producto p WHERE p.numeroInterno = :numeroInterno AND p.estaActivo = true AND p.eliminadoEn IS NULL")
    Optional<Producto> buscarPorNumeroInterno(@Param("numeroInterno") String numeroInterno);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.numeroInterno = :numeroInterno AND p.eliminadoEn IS NULL")
    boolean existePorNumeroInterno(@Param("numeroInterno") String numeroInterno);

    @Query("SELECT COUNT(p) > 0 FROM Producto p WHERE p.numeroInterno = :numeroInterno AND p.id <> :id AND p.eliminadoEn IS NULL")
    boolean existePorNumeroInternoExcluyendoId(@Param("numeroInterno") String numeroInterno, @Param("id") Long id);

    @Query("SELECT p FROM Producto p WHERE p.estaActivo = true AND p.eliminadoEn IS NULL ORDER BY p.nombre")
    Page<Producto> listarActivos(Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.estaActivo = true AND p.eliminadoEn IS NULL AND p.stockActual <= p.stockMinimo")
    List<Producto> listarConStockBajoMinimo();

    @Query("SELECT p FROM Producto p WHERE p.estaActivo = true AND p.eliminadoEn IS NULL AND p.mostrarEnListaPrecios = true ORDER BY p.nombre")
    List<Producto> listarParaListaPrecios();
}