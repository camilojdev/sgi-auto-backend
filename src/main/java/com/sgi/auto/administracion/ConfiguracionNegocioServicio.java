package com.sgi.auto.administracion;

import com.sgi.auto.administracion.dto.ConfiguracionNegocioActualizarDTO;
import com.sgi.auto.administracion.dto.ConfiguracionNegocioRespuestaDTO;
import com.sgi.auto.compartido.ImagenServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ConfiguracionNegocioServicio {

    private final ConfiguracionNegocioRepositorio repositorio;
    private final ImagenServicio imagenServicio;

    @Transactional(readOnly = true)
    public ConfiguracionNegocioRespuestaDTO obtener() {
        return aDTO(repositorio.obtenerConfiguracion());
    }

    @Transactional
    public ConfiguracionNegocioRespuestaDTO actualizar(ConfiguracionNegocioActualizarDTO dto, Long usuarioId) {
        ConfiguracionNegocio config = repositorio.obtenerConfiguracion();
        config.setNombreNegocio(dto.nombreNegocio());
        config.setEslogan(dto.eslogan());
        config.setDireccion(dto.direccion());
        config.setTelefono(dto.telefono());
        config.setNit(dto.nit());
        config.setPiePaginaFactura(dto.piePaginaFactura());
        config.setActualizadoEn(OffsetDateTime.now());
        config.setActualizadoPor(usuarioId);
        return aDTO(repositorio.save(config));
    }

    @Transactional
    public ConfiguracionNegocioRespuestaDTO actualizarLogo(MultipartFile archivo, Long usuarioId) {
        ConfiguracionNegocio config = repositorio.obtenerConfiguracion();
        if (config.getLogoPublicId() != null) {
            imagenServicio.eliminar(config.getLogoPublicId());
        }
        var resultado = imagenServicio.subir(archivo, "sgi-auto/negocio");
        config.setLogoUrl(resultado.url());
        config.setLogoPublicId(resultado.publicId());
        config.setActualizadoEn(OffsetDateTime.now());
        config.setActualizadoPor(usuarioId);
        return aDTO(repositorio.save(config));
    }

    @Transactional
    public ConfiguracionNegocioRespuestaDTO actualizarLogoEtiquetas(MultipartFile archivo, Long usuarioId) {
        ConfiguracionNegocio config = repositorio.obtenerConfiguracion();
        if (config.getLogoEtiquetasPublicId() != null) {
            imagenServicio.eliminar(config.getLogoEtiquetasPublicId());
        }
        var resultado = imagenServicio.subirPngTransparente(archivo, "sgi-auto/etiquetas");
        config.setLogoEtiquetasUrl(resultado.url());
        config.setLogoEtiquetasPublicId(resultado.publicId());
        config.setActualizadoEn(OffsetDateTime.now());
        config.setActualizadoPor(usuarioId);
        return aDTO(repositorio.save(config));
    }

    private ConfiguracionNegocioRespuestaDTO aDTO(ConfiguracionNegocio c) {
        return new ConfiguracionNegocioRespuestaDTO(
                c.getNombreNegocio(), c.getEslogan(), c.getLogoUrl(),
                c.getLogoEtiquetasUrl(),
                c.getDireccion(), c.getTelefono(), c.getNit(), c.getPiePaginaFactura());
    }
}