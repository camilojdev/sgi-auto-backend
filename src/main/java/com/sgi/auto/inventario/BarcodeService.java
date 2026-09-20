package com.sgi.auto.inventario;

import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.ProductoIdentificadoDTO;
import com.sgi.auto.inventario.dto.ProductoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Punto único de acceso para todo lo relacionado a códigos de producto:
 * validación de formato y resolución de búsquedas (código vigente,
 * historial, número interno, nombre). La generación de la imagen del
 * código de barras para PDFs se agrega en la fase de integración con
 * Factura/OT, cuando ya exista un PdfDocument real contra el cual probarla.
 */
@Service
@RequiredArgsConstructor
public class BarcodeService {

    private final ProductoRepositorio productoRepositorio;
    private final ProductoCodigoRepositorio productoCodigoRepositorio;
    private final ProductoMapper productoMapper;

    private static final int LONGITUD_MAXIMA_CODE128 = 48;

    public void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioExcepcion("El código no puede estar vacío");
        }
        if (codigo.length() > LONGITUD_MAXIMA_CODE128) {
            throw new ReglaNegocioExcepcion(
                    "El código es demasiado largo para representarse como código de barras (máx. "
                            + LONGITUD_MAXIMA_CODE128 + " caracteres)");
        }
    }

    /**
     * Búsqueda en cascada, pensada para lectores de código de barras y
     * búsquedas exactas:
     * 1. Código vigente del producto.
     * 2. Historial de códigos (código anterior, ya desactivado).
     * 3. Número interno.
     * 4. Nombre, solo si hay una coincidencia única.
     */
    @Transactional(readOnly = true)
    public ProductoIdentificadoDTO identificarProducto(String termino) {
        if (termino == null || termino.isBlank()) {
            throw new ReglaNegocioExcepcion("Debes indicar un código, número interno o nombre a buscar");
        }
        String t = termino.trim();

        Optional<Producto> porCodigoVigente = productoRepositorio.buscarPorCodigo(t);
        if (porCodigoVigente.isPresent()) {
            return new ProductoIdentificadoDTO(productoMapper.aDTO(porCodigoVigente.get()), false);
        }

        Optional<ProductoCodigo> enHistorial = productoCodigoRepositorio.findFirstByCodigoOrderByFechaCreacionDesc(t);
        if (enHistorial.isPresent()) {
            Producto producto = enHistorial.get().getProducto();
            if (producto.isEstaActivo() && producto.getEliminadoEn() == null) {
                return new ProductoIdentificadoDTO(productoMapper.aDTO(producto), true);
            }
        }

        Optional<Producto> porNumeroInterno = productoRepositorio.buscarPorNumeroInterno(t);
        if (porNumeroInterno.isPresent()) {
            return new ProductoIdentificadoDTO(productoMapper.aDTO(porNumeroInterno.get()), false);
        }

        List<Producto> porNombre = productoRepositorio.buscarPorNombre(t);
        if (porNombre.size() == 1) {
            return new ProductoIdentificadoDTO(productoMapper.aDTO(porNombre.get(0)), false);
        }

        throw new RecursoNoEncontradoExcepcion(
                "No se encontró un único producto que coincida con: " + termino);
    }
}