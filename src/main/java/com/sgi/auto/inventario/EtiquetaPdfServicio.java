package com.sgi.auto.inventario;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sgi.auto.administracion.ConfiguracionNegocio;
import com.sgi.auto.administracion.ConfiguracionNegocioRepositorio;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.compartido.ReglaNegocioExcepcion;
import com.sgi.auto.inventario.dto.GenerarEtiquetasDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtiquetaPdfServicio {

    private final ProductoRepositorio productoRepositorio;
    private final PlantillaEtiquetaRepositorio plantillaRepositorio;
    private final ConfiguracionNegocioRepositorio configuracionNegocioRepositorio;
    private final BarcodeService barcodeService;

    private static final float MM_A_PT = 72f / 25.4f;

    @Transactional(readOnly = true)
    public byte[] generar(GenerarEtiquetasDTO solicitud) {
        PlantillaEtiqueta plantilla = plantillaRepositorio.findByCodigo(solicitud.plantillaCodigo())
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No existe la plantilla: " + solicitud.plantillaCodigo()));

        List<Producto> items = new ArrayList<>();
        for (GenerarEtiquetasDTO.ItemEtiquetaDTO item : solicitud.items()) {
            Producto producto = productoRepositorio.findById(item.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                            "No se encontró el producto con id: " + item.productoId()));
            for (int i = 0; i < item.cantidad(); i++) {
                items.add(producto);
            }
        }
        if (items.isEmpty()) {
            throw new ReglaNegocioExcepcion("Debes agregar al menos un producto con cantidad mayor a cero");
        }

        String logoUrl = configuracionNegocioRepositorio.findById(1L)
                .map(ConfiguracionNegocio::getLogoEtiquetasUrl)
                .orElse(null);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(salida);
             PdfDocument pdf = new PdfDocument(writer)) {

            if ("TERMICA".equals(plantilla.getTipo())) {
                generarTermica(pdf, plantilla, items, logoUrl);
            } else {
                generarHoja(pdf, plantilla, items, logoUrl);
            }
        } catch (Exception e) {
            log.error("Error generando PDF de etiquetas: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el PDF de etiquetas: " + e.getMessage());
        }

        return salida.toByteArray();
    }

    private void generarHoja(PdfDocument pdf, PlantillaEtiqueta plantilla, List<Producto> items, String logoUrl) {
        float anchoEtiquetaPt = plantilla.getAnchoMm().floatValue() * MM_A_PT;
        float altoEtiquetaPt = plantilla.getAltoMm().floatValue() * MM_A_PT;
        float margenSupPt = plantilla.getMargenSuperiorMm().floatValue() * MM_A_PT;
        float margenIzqPt = plantilla.getMargenIzquierdoMm().floatValue() * MM_A_PT;
        float sepHPt = plantilla.getSeparacionHorizontalMm().floatValue() * MM_A_PT;
        float sepVPt = plantilla.getSeparacionVerticalMm().floatValue() * MM_A_PT;

        PageSize tamanoPagina = PageSize.A4;

        int indice = 0;
        while (indice < items.size()) {
            PdfPage page = pdf.addNewPage(tamanoPagina);
            for (int fila = 0; fila < plantilla.getFilas() && indice < items.size(); fila++) {
                for (int col = 0; col < plantilla.getColumnas() && indice < items.size(); col++) {
                    float x = margenIzqPt + col * (anchoEtiquetaPt + sepHPt);
                    float yDesdeArriba = margenSupPt + fila * (altoEtiquetaPt + sepVPt);
                    float y = tamanoPagina.getHeight() - yDesdeArriba - altoEtiquetaPt;

                    Rectangle rect = new Rectangle(x, y, anchoEtiquetaPt, altoEtiquetaPt);
                    dibujarEtiqueta(page, rect, items.get(indice), logoUrl);
                    indice++;
                }
            }
        }
    }

    private void generarTermica(PdfDocument pdf, PlantillaEtiqueta plantilla, List<Producto> items, String logoUrl) {
        float anchoPt = plantilla.getAnchoMm().floatValue() * MM_A_PT;
        float altoPt = plantilla.getAltoMm().floatValue() * MM_A_PT;
        PageSize tamanoEtiqueta = new PageSize(anchoPt, altoPt);

        for (Producto producto : items) {
            PdfPage page = pdf.addNewPage(tamanoEtiqueta);
            Rectangle rect = new Rectangle(0, 0, anchoPt, altoPt);
            dibujarEtiqueta(page, rect, producto, logoUrl);
        }
    }

    private void dibujarEtiqueta(PdfPage page, Rectangle rect, Producto producto, String logoUrl) {
        Rectangle areaInterna = new Rectangle(
                rect.getX() + 2,
                rect.getY() + 2,
                rect.getWidth() - 4,
                rect.getHeight() - 4
        );

        Canvas canvas = new Canvas(page, areaInterna);

        // Fila superior: logo + nombre del producto
        Table filaSuperior = new Table(UnitValue.createPercentArray(new float[]{1, 4})).useAllAvailableWidth();

        Cell celdaLogo = new Cell().setBorder(Border.NO_BORDER).setPadding(1);
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(logoUrl));
                logo.setWidth(14).setHeight(14);
                celdaLogo.add(logo);
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo de etiquetas: {}", e.getMessage());
            }
        }
        filaSuperior.addCell(celdaLogo);
        filaSuperior.addCell(new Cell()
                .add(new Paragraph(producto.getNombre().toUpperCase())
                        .setBold().setFontSize(6).setMultipliedLeading(0.9f))
                .setBorder(Border.NO_BORDER).setPadding(1));
        canvas.add(filaSuperior);

        // Precio oculto (letras), si la dueña lo definió para este producto
        if (producto.getPrecioOculto() != null && !producto.getPrecioOculto().isBlank()) {
            canvas.add(new Paragraph(producto.getPrecioOculto())
                    .setBold().setFontSize(7)
                    .setFontColor(ColorConstants.RED)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMultipliedLeading(0.9f));
        }

        // Código de barras Code128
        try {
            Image barras = barcodeService.generarImagenCodigoBarras(page.getDocument(), producto.getCodigo());
            barras.setAutoScale(true);
            canvas.add(barras);
        } catch (Exception e) {
            canvas.add(new Paragraph("(código no disponible)").setFontSize(5));
        }

        // Código visible debajo del código de barras
        canvas.add(new Paragraph(producto.getCodigo())
                .setFontSize(6).setTextAlignment(TextAlignment.CENTER).setMultipliedLeading(0.9f));

        canvas.close();
    }
}