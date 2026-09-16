package com.sgi.auto.compartido;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImagenServicio {

    private final Cloudinary cloudinary;

    private static final long TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final List<String> TIPOS_PERMITIDOS =
            List.of("image/jpeg", "image/png", "image/webp");

    public record ResultadoSubidaImagen(String url, String publicId) {}

    public ResultadoSubidaImagen subir(MultipartFile archivo, String carpeta) {
        validar(archivo);
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(archivo.getBytes(), ObjectUtils.asMap(
                    "folder", carpeta,
                    "resource_type", "image",
                    "quality", "auto",
                    "fetch_format", "auto",
                    "overwrite", true
            ));
            String url = (String) resultado.get("secure_url");
            String publicId = (String) resultado.get("public_id");
            log.info("Imagen subida a Cloudinary: carpeta={}, publicId={}", carpeta, publicId);
            return new ResultadoSubidaImagen(url, publicId);
        } catch (IOException e) {
            log.error("Error subiendo imagen a Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo subir la imagen: " + e.getMessage());
        }
    }

    public void eliminar(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Imagen eliminada de Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            log.warn("No se pudo eliminar la imagen anterior: publicId={}, error={}", publicId, e.getMessage());
        }
    }

    private void validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioExcepcion("Debes seleccionar un archivo de imagen");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new ReglaNegocioExcepcion("La imagen no puede superar los 5 MB");
        }
        if (!TIPOS_PERMITIDOS.contains(archivo.getContentType())) {
            throw new ReglaNegocioExcepcion("Formato de imagen no permitido. Usa JPG, PNG o WEBP");
        }
    }
}