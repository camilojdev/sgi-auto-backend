package com.sgi.auto.inventario;

import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.element.Image;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.ProductoIdentificadoDTO;
import com.sgi.auto.inventario.dto.ProductoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
     * Genera la imagen del código de barras Code128 lista para insertar en un PDF.
     * El texto legible se dibuja aparte por quien llama, para tener control total
     * de tamaño y tipografía — por eso se desactiva el texto propio del código de barras.
     */
    public Image generarImagenCodigoBarras(PdfDocument pdfDocument, String codigo) {
        validarCodigo(codigo);
        Barcode128 barcode128 = new Barcode128(pdfDocument);
        barcode128.setCodeType(Barcode128.CODE128);
        barcode128.setCode(codigo);
        barcode128.setFont(null);
        PdfFormXObject xObject = barcode128.createFormXObject(ColorConstants.BLACK, ColorConstants.BLACK, pdfDocument);
        return new Image(xObject);
    }

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