package com.sgi.auto.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlantillaEtiquetaRepositorio extends JpaRepository<PlantillaEtiqueta, Long> {
    Optional<PlantillaEtiqueta> findByCodigo(String codigo);
}