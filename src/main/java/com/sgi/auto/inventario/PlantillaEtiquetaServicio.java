package com.sgi.auto.inventario;

import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.PlantillaEtiquetaActualizarDTO;
import com.sgi.auto.inventario.dto.PlantillaEtiquetaRespuestaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantillaEtiquetaServicio {

    private final PlantillaEtiquetaRepositorio repositorio;

    @Transactional(readOnly = true)
    public List<PlantillaEtiquetaRespuestaDTO> listar() {
        return repositorio.findAll().stream().map(this::aDTO).toList();
    }

    @Transactional
    public PlantillaEtiquetaRespuestaDTO actualizar(String codigo, PlantillaEtiquetaActualizarDTO dto) {
        PlantillaEtiqueta plantilla = repositorio.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion("No existe la plantilla: " + codigo));

        if (dto.anchoMm().compareTo(BigDecimal.ZERO) <= 0 || dto.altoMm().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ReglaNegocioExcepcion("El ancho y el alto deben ser mayores a cero");
        }

        plantilla.setAnchoMm(dto.anchoMm());
        plantilla.setAltoMm(dto.altoMm());
        plantilla.setColumnas(dto.columnas());
        plantilla.setFilas(dto.filas());
        plantilla.setMargenSuperiorMm(dto.margenSuperiorMm());
        plantilla.setMargenIzquierdoMm(dto.margenIzquierdoMm());
        plantilla.setSeparacionHorizontalMm(dto.separacionHorizontalMm());
        plantilla.setSeparacionVerticalMm(dto.separacionVerticalMm());
        plantilla.setActualizadoEn(OffsetDateTime.now());

        return aDTO(repositorio.save(plantilla));
    }

    private PlantillaEtiquetaRespuestaDTO aDTO(PlantillaEtiqueta p) {
        return new PlantillaEtiquetaRespuestaDTO(
                p.getCodigo(), p.getNombre(), p.getTipo(),
                p.getAnchoMm(), p.getAltoMm(), p.getColumnas(), p.getFilas(),
                p.getMargenSuperiorMm(), p.getMargenIzquierdoMm(),
                p.getSeparacionHorizontalMm(), p.getSeparacionVerticalMm());
    }
}