package com.sgi.auto.administracion;

import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionNegocioRepositorio extends JpaRepository<ConfiguracionNegocio, Long> {

    default ConfiguracionNegocio obtenerConfiguracion() {
        return findById(1L).orElseThrow(() ->
                new RecursoNoEncontradoExcepcion("La configuración del negocio no ha sido inicializada"));
    }
}