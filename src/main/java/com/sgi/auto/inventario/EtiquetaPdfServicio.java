package com.sgi.auto.inventario;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
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

/**
 * Genera el PDF de etiquetas replicando EXACTAMENTE el diseño aprobado en Canva
 * ("Etiquetas 3x10 y 4x10", https://www.canva.com/design/DAHVpSM4D5Y).
 *
 * IMPORTANTE: las plantillas de 3 y de 4 columnas NO son la misma etiqueta escalada.
 * Son dos diseños independientes en Canva (tamaños de fuente distintos, logo circular
 * vs. logo rectangular, posiciones distintas). Por eso aquí se definen DOS perfiles
 * de diseño ({@link #PERFIL_3_COLUMNAS} y {@link #PERFIL_4_COLUMNAS}) medidos por
 * separado, en vez de escalar un único perfil de referencia.
 *
 * Todas las coordenadas de cada perfil están medidas en milímetros y son relativas
 * a la esquina superior izquierda de la ETIQUETA (no de la página), asumiendo que
 * el ancho/alto real de la plantilla en BD coincide con refAnchoMm/refAltoMm del
 * perfil. Si en BD configuras un ancho/alto distinto, el código reescala
 * proporcionalmente (scaleFuente) para no romper el layout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtiquetaPdfServicio {

    private final ProductoRepositorio productoRepositorio;
    private final PlantillaEtiquetaRepositorio plantillaRepositorio;
    private final ConfiguracionNegocioRepositorio configuracionNegocioRepositorio;
    private final BarcodeService barcodeService;

    private static final float MM_A_PT = 72f / 25.4f;

    private static final DeviceRgb NEGRO_TEXTO = new DeviceRgb(0x11, 0x11, 0x11);
    private static final DeviceRgb ROJO_PRECIO = new DeviceRgb(0xE5, 0x24, 0x24);

    /**
     * Fuente usada ÚNICAMENTE para MEDIR el ancho del texto (contarLineas /
     * ajustarFontParaAncho) y decidir si hay que achicar la letra del nombre.
     *
     * IMPORTANTE: nunca se debe llamar a Paragraph.setFont(FUENTE_NEGRITA) ni
     * agregar esta instancia a un Document real. iText "ata" el objeto de
     * fuente al primer PdfDocument en el que se usa; como este campo es
     * static (se reutiliza entre peticiones, cada una con su propio
     * PdfDocument nuevo), si se le hace setFont() a un elemento y se agrega al
     * documento, la SIGUIENTE petición falla con:
     * "Pdf indirect object belongs to other PDF document". Por eso el
     * Paragraph del nombre se sigue dibujando con .setBold() (fuente nueva e
     * independiente en cada documento), y esta fuente aquí solo se usa para
     * los cálculos de font.getWidth(...).
     */
    private static final PdfFont FUENTE_NEGRITA = crearFuenteNegrita();

    private static PdfFont crearFuenteNegrita() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la fuente Helvetica-Bold", e);
        }
    }

    // ------------------------------------------------------------------
    // Definición de un perfil de diseño (una plantilla completa de Canva)
    // ------------------------------------------------------------------
    private record Perfil(
            String nombre,
            float refAnchoMm, float refAltoMm,
            boolean logoCircular,
            float logoLeftMm, float logoTopMm, float logoAnchoMm, float logoAltoMm,
            float nombreLeftMm, float nombreTopMm, float nombreAnchoMm, float nombreAltoMm,
            float nombreFontPt, float nombreLineHeight,
            float precioLeftMm, float precioTopMm, float precioAnchoMm, float precioAltoMm,
            float precioFontPt, float precioLineHeight,
            float barcodeLeftMm, float barcodeTopMm, float barcodeAnchoMm, float barcodeAltoMm,
            float codigoLeftMm, float codigoTopMm, float codigoAnchoMm, float codigoAltoMm,
            float codigoFontPt, float codigoLineHeight
    ) {
    }

    /** Página "3x10 en A4" de Canva: 3 columnas x 10 filas, etiqueta de 69.00 x 29.10 mm, logo circular. */
    private static final Perfil PERFIL_3_COLUMNAS = new Perfil(
            "3 columnas (A4_30)",
            69.0000f, 29.1000f,
            true,
            0.5910f, 1.0777f, 15.2019f, 15.2019f,
            17.2055f, 2.0774f, 50.3721f, 7.3378f, 13.3333f, 0.92f,
            1.7493f, 11.8669f, 65.8283f, 3.5983f, 12.0000f, 0.92f,
            5.9827f, 17.4232f, 57.3617f, 5.0271f,
            1.7493f, 24.0378f, 65.8283f, 3.1397f, 10.6667f, 0.92f
    );

    /** Página "4x10 en A4" de Canva: 4 columnas x 10 filas, etiqueta de 51.75 x 29.10 mm, logo rectangular. */
    private static final Perfil PERFIL_4_COLUMNAS = new Perfil(
            "4 columnas (A4_40)",
            51.7245f, 29.1043f,
            false,
            1.0095f, 1.2693f, 9.7085f, 9.4533f,
            11.1550f, 2.0272f, 38.8650f, 6.2420f, 10.6667f, 1.05f,
            2.0272f, 13.6680f, 46.8300f, 4.2870f, 14.0000f, 0.92f,
            2.5615f, 18.6826f, 45.7729f, 4.7625f,
            2.0272f, 24.5160f, 46.8300f, 3.6193f, 11.6000f, 0.92f
    );

    @Transactional(readOnly = true)
    public byte[] generar(GenerarEtiquetasDTO solicitud) {

        PlantillaEtiqueta plantilla = plantillaRepositorio.findByCodigo(solicitud.plantillaCodigo())
                .orElseThrow(() ->
                        new RecursoNoEncontradoExcepcion("No existe la plantilla: " + solicitud.plantillaCodigo()));

        List<Producto> items = new ArrayList<>();
        for (GenerarEtiquetasDTO.ItemEtiquetaDTO item : solicitud.items()) {
            Producto producto = productoRepositorio.findById(item.productoId())
                    .orElseThrow(() ->
                            new RecursoNoEncontradoExcepcion(
                                    "No se encontró el producto con id: " + item.productoId()));
            for (int i = 0; i < item.cantidad(); i++) {
                items.add(producto);
            }
        }

        if (items.isEmpty()) {
            throw new ReglaNegocioExcepcion("Debes agregar al menos un producto con cantidad mayor a cero.");
        }

        String logoUrl = configuracionNegocioRepositorio.findById(1L)
                .map(ConfiguracionNegocio::getLogoEtiquetasUrl)
                .orElse(null);

        Perfil perfil = seleccionarPerfil(plantilla);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(salida);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.setMargins(0, 0, 0, 0);

            if ("TERMICA".equals(plantilla.getTipo())) {
                generarTermica(doc, pdf, plantilla, items, logoUrl, perfil);
            } else {
                generarHoja(doc, pdf, plantilla, items, logoUrl, perfil);
            }

        } catch (Exception e) {
            log.error("Error generando PDF de etiquetas: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo generar el PDF de etiquetas: " + e.getMessage(), e);
        }

        return salida.toByteArray();
    }

    /**
     * Elige qué perfil de diseño usar según la cantidad de columnas configurada
     * en la plantilla. Si algún día agregas una tercera plantilla de Canva con
     * otra cantidad de columnas, hay que medirla igual que estas dos y añadir
     * un tercer Perfil aquí.
     */
    private Perfil seleccionarPerfil(PlantillaEtiqueta plantilla) {
        int columnas = plantilla.getColumnas();
        if (columnas == 3) {
            return PERFIL_3_COLUMNAS;
        }
        if (columnas == 4) {
            return PERFIL_4_COLUMNAS;
        }
        // Plantilla térmica u otra configuración: se usa el perfil cuyo ancho de
        // referencia esté más cerca del ancho configurado, para no romper el PDF.
        float anchoMm = plantilla.getAnchoMm().floatValue();
        float distA = Math.abs(anchoMm - PERFIL_3_COLUMNAS.refAnchoMm());
        float distB = Math.abs(anchoMm - PERFIL_4_COLUMNAS.refAnchoMm());
        Perfil elegido = distA <= distB ? PERFIL_3_COLUMNAS : PERFIL_4_COLUMNAS;
        log.warn("Plantilla '{}' tiene columnas={} (no es 3 ni 4); usando perfil '{}' como aproximación.",
                plantilla.getCodigo(), columnas, elegido.nombre());
        return elegido;
    }

    private void generarHoja(Document doc, PdfDocument pdf, PlantillaEtiqueta plantilla,
                             List<Producto> items, String logoUrl, Perfil perfil) {

        float anchoEtiquetaPt = plantilla.getAnchoMm().floatValue() * MM_A_PT;
        float altoEtiquetaPt = plantilla.getAltoMm().floatValue() * MM_A_PT;
        float margenSupPt = plantilla.getMargenSuperiorMm().floatValue() * MM_A_PT;
        float margenIzqPt = plantilla.getMargenIzquierdoMm().floatValue() * MM_A_PT;
        float sepHPt = plantilla.getSeparacionHorizontalMm().floatValue() * MM_A_PT;
        float sepVPt = plantilla.getSeparacionVerticalMm().floatValue() * MM_A_PT;

        PageSize tamanoPagina = PageSize.A4;

        int indice = 0;
        while (indice < items.size()) {

            pdf.addNewPage(tamanoPagina);
            int numeroPagina = pdf.getNumberOfPages();

            for (int fila = 0; fila < plantilla.getFilas() && indice < items.size(); fila++) {
                for (int col = 0; col < plantilla.getColumnas() && indice < items.size(); col++) {

                    float x = margenIzqPt + col * (anchoEtiquetaPt + sepHPt);
                    float yDesdeArriba = margenSupPt + fila * (altoEtiquetaPt + sepVPt);
                    float y = tamanoPagina.getHeight() - yDesdeArriba - altoEtiquetaPt;

                    Rectangle rect = new Rectangle(x, y, anchoEtiquetaPt, altoEtiquetaPt);

                    dibujarLineaCorte(pdf, numeroPagina, rect);
                    dibujarEtiqueta(doc, pdf, numeroPagina, rect, items.get(indice), logoUrl, perfil);

                    indice++;
                }
            }
        }
    }

    private void generarTermica(Document doc, PdfDocument pdf, PlantillaEtiqueta plantilla,
                                List<Producto> items, String logoUrl, Perfil perfil) {

        float anchoPt = plantilla.getAnchoMm().floatValue() * MM_A_PT;
        float altoPt = plantilla.getAltoMm().floatValue() * MM_A_PT;

        PageSize tamanoEtiqueta = new PageSize(anchoPt, altoPt);

        for (Producto producto : items) {

            pdf.addNewPage(tamanoEtiqueta);
            int numeroPagina = pdf.getNumberOfPages();

            Rectangle rect = new Rectangle(0, 0, anchoPt, altoPt);

            dibujarEtiqueta(doc, pdf, numeroPagina, rect, producto, logoUrl, perfil);
        }
    }

    /**
     * Dibuja un rectángulo punteado del tamaño exacto de la etiqueta, como guía
     * visual para recortar con tijeras. Solo aplica a la hoja A4 (no a la
     * plantilla térmica, donde cada página YA es del tamaño exacto de la etiqueta).
     */
    private void dibujarLineaCorte(PdfDocument pdf, int numeroPagina, Rectangle labelRect) {
        PdfPage page = pdf.getPage(numeroPagina);
        PdfCanvas pdfCanvas = new PdfCanvas(page);

        pdfCanvas.saveState();
        pdfCanvas.setLineWidth(0.4f);
        pdfCanvas.setLineDash(2f, 2f);
        pdfCanvas.setStrokeColor(new DeviceRgb(0xBF, 0xBF, 0xBF));
        pdfCanvas.rectangle(labelRect);
        pdfCanvas.stroke();
        pdfCanvas.restoreState();
    }

    private void dibujarEtiqueta(Document doc, PdfDocument pdf, int numeroPagina,
                                 Rectangle labelRect, Producto producto, String logoUrl, Perfil perfil) {

        float anchoActualMm = labelRect.getWidth() / MM_A_PT;
        float altoActualMm = labelRect.getHeight() / MM_A_PT;

        float scaleX = anchoActualMm / perfil.refAnchoMm();
        float scaleY = altoActualMm / perfil.refAltoMm();
        float scaleFuente = Math.min(scaleX, scaleY);

        // ---- Logo ----
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                float logoLeftPt = perfil.logoLeftMm() * scaleX * MM_A_PT;
                float logoTopPt = perfil.logoTopMm() * scaleY * MM_A_PT;
                float logoAnchoPt = perfil.logoAnchoMm() * scaleX * MM_A_PT;
                float logoAltoPt = perfil.logoAltoMm() * scaleY * MM_A_PT;

                if (perfil.logoCircular()) {
                    dibujarLogoCircular(pdf, numeroPagina, labelRect, logoLeftPt, logoTopPt,
                            logoAnchoPt, logoAltoPt, logoUrl);
                } else {
                    Image logo = new Image(ImageDataFactory.create(logoUrl));
                    posicionarAbsoluto(logo, numeroPagina, labelRect, logoLeftPt, logoTopPt, logoAnchoPt, logoAltoPt);
                    doc.add(logo);
                }

            } catch (Exception e) {
                log.warn("No se pudo cargar el logo de etiquetas: {}", e.getMessage());
            }
        }

        // ---- Nombre del producto (con auto-ajuste de tamaño si el nombre es largo) ----
        String nombreTexto = producto.getNombre().toUpperCase();
        float nombreAnchoPt = perfil.nombreAnchoMm() * scaleX * MM_A_PT;
        float nombreFontBasePt = perfil.nombreFontPt() * scaleFuente;
        float nombreFontMinimoPt = Math.max(nombreFontBasePt * 0.55f, 6f * scaleFuente);

        float nombreFontFinalPt = ajustarFontParaAncho(
                nombreTexto, FUENTE_NEGRITA, nombreFontBasePt, nombreFontMinimoPt, nombreAnchoPt, 2
        );

        Paragraph nombre = new Paragraph(nombreTexto)
                .setBold()
                .setFontSize(nombreFontFinalPt)
                .setFontColor(NEGRO_TEXTO)
                .setMultipliedLeading(perfil.nombreLineHeight())
                .setMargin(0);

        posicionarTexto(
                nombre, numeroPagina, labelRect,
                perfil.nombreLeftMm() * scaleX * MM_A_PT,
                perfil.nombreTopMm() * scaleY * MM_A_PT,
                nombreAnchoPt
        );
        doc.add(nombre);

        // ---- Precio oculto (opcional) ----
        if (producto.getPrecioOculto() != null && !producto.getPrecioOculto().isBlank()) {

            Paragraph precio = new Paragraph(producto.getPrecioOculto())
                    .setBold()
                    .setFontSize(perfil.precioFontPt() * scaleFuente)
                    .setFontColor(ROJO_PRECIO)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMultipliedLeading(perfil.precioLineHeight())
                    .setMargin(0);

            posicionarTexto(
                    precio, numeroPagina, labelRect,
                    perfil.precioLeftMm() * scaleX * MM_A_PT,
                    perfil.precioTopMm() * scaleY * MM_A_PT,
                    perfil.precioAnchoMm() * scaleX * MM_A_PT
            );
            doc.add(precio);
        }

        // ---- Código de barras ----
        try {
            Image barras = barcodeService.generarImagenCodigoBarras(doc.getPdfDocument(), producto.getCodigo());

            posicionarAbsoluto(
                    barras, numeroPagina, labelRect,
                    perfil.barcodeLeftMm() * scaleX * MM_A_PT,
                    perfil.barcodeTopMm() * scaleY * MM_A_PT,
                    perfil.barcodeAnchoMm() * scaleX * MM_A_PT,
                    perfil.barcodeAltoMm() * scaleY * MM_A_PT
            );
            doc.add(barras);

        } catch (Exception e) {
            log.warn("No se pudo generar el código de barras: {}", e.getMessage());
        }

        // ---- Código visible ----
        Paragraph codigo = new Paragraph(producto.getCodigo())
                .setBold()
                .setFontSize(perfil.codigoFontPt() * scaleFuente)
                .setFontColor(NEGRO_TEXTO)
                .setTextAlignment(TextAlignment.CENTER)
                .setMultipliedLeading(perfil.codigoLineHeight())
                .setMargin(0);

        posicionarTexto(
                codigo, numeroPagina, labelRect,
                perfil.codigoLeftMm() * scaleX * MM_A_PT,
                perfil.codigoTopMm() * scaleY * MM_A_PT,
                perfil.codigoAnchoMm() * scaleX * MM_A_PT
        );
        doc.add(codigo);
    }

    /**
     * Dibuja el logo recortado en círculo (tal como está en la plantilla de 3 columnas
     * de Canva), usando el canvas de bajo nivel para poder aplicar un clip circular.
     */
    private void dibujarLogoCircular(PdfDocument pdf, int numeroPagina, Rectangle labelRect,
                                     float leftPt, float topPt, float anchoPt, float altoPt,
                                     String logoUrl) throws Exception {

        float x = labelRect.getX() + leftPt;
        float yTop = labelRect.getY() + labelRect.getHeight() - topPt;
        float yBottom = yTop - altoPt;

        float radioX = anchoPt / 2f;
        float radioY = altoPt / 2f;
        float cx = x + radioX;
        float cy = yBottom + radioY;

        PdfPage page = pdf.getPage(numeroPagina);
        PdfCanvas pdfCanvas = new PdfCanvas(page);

        ImageData imageData = ImageDataFactory.create(logoUrl);

        pdfCanvas.saveState();
        // Aproximación de círculo con 4 curvas Bézier (recorte circular).
        float k = 0.5522847498f; // constante de aproximación de círculo con Bézier
        pdfCanvas.moveTo(cx + radioX, cy);
        pdfCanvas.curveTo(cx + radioX, cy + radioY * k, cx + radioX * k, cy + radioY, cx, cy + radioY);
        pdfCanvas.curveTo(cx - radioX * k, cy + radioY, cx - radioX, cy + radioY * k, cx - radioX, cy);
        pdfCanvas.curveTo(cx - radioX, cy - radioY * k, cx - radioX * k, cy - radioY, cx, cy - radioY);
        pdfCanvas.curveTo(cx + radioX * k, cy - radioY, cx + radioX, cy - radioY * k, cx + radioX, cy);
        pdfCanvas.closePath();
        pdfCanvas.clip();
        pdfCanvas.endPath();
        pdfCanvas.addImageFittedIntoRectangle(imageData, new Rectangle(x, yBottom, anchoPt, altoPt), false);
        pdfCanvas.restoreState();
    }

    private void posicionarAbsoluto(Image elemento, int numeroPagina, Rectangle labelRect,
                                    float leftPt, float topPt, float anchoPt, float altoPt) {

        float x = labelRect.getX() + leftPt;
        float yTop = labelRect.getY() + labelRect.getHeight() - topPt;
        float yBottom = yTop - altoPt;

        elemento.setFixedPosition(numeroPagina, x, yBottom, anchoPt);
        elemento.setHeight(altoPt);
    }

    private void posicionarAbsoluto(Paragraph elemento, int numeroPagina, Rectangle labelRect,
                                    float leftPt, float topPt, float anchoPt, float altoPt) {

        float x = labelRect.getX() + leftPt;
        float yTop = labelRect.getY() + labelRect.getHeight() - topPt;
        float yBottom = yTop - altoPt;

        elemento.setFixedPosition(numeroPagina, x, yBottom, anchoPt);
        elemento.setHeight(altoPt);
    }

    /**
     * Posiciona un párrafo de texto dejando el borde superior exactamente en
     * "topPt" (igual que en Canva), pero con espacio de sobra hacia abajo
     * (hasta el fondo de la etiqueta) en vez de una caja del alto exacto medido
     * en Canva. Esto es a propósito: la fuente de PDF (Helvetica) no tiene
     * exactamente el mismo interlineado que la fuente de Canva, así que si se
     * fuerza una caja del alto "justo" medido en Canva, iText puede decidir que
     * el texto no cabe y simplemente NO dibujarlo (esto fue lo que causaba que
     * el precio oculto y el código no aparecieran). Al dejar espacio de sobra
     * hacia abajo, el texto sigue naciendo en la misma posición superior, pero
     * ya no se corre el riesgo de que se descarte por falta de espacio.
     */
    private void posicionarTexto(Paragraph elemento, int numeroPagina, Rectangle labelRect,
                                 float leftPt, float topPt, float anchoPt) {

        float x = labelRect.getX() + leftPt;
        float yTop = labelRect.getY() + labelRect.getHeight() - topPt;
        float yBottom = labelRect.getY();
        float altoDisponible = yTop - yBottom;

        elemento.setFixedPosition(numeroPagina, x, yBottom, anchoPt);
        elemento.setHeight(altoDisponible);
    }

    /**
     * Cuenta en cuántas líneas quedaría el texto si se ajusta (word-wrap) dentro
     * de un ancho máximo, con la fuente y tamaño dados. Es una simulación simple
     * (por palabras) para poder decidir si hay que achicar la letra, sin tener
     * que crear todavía el Paragraph real dentro del documento.
     */
    private int contarLineas(String texto, PdfFont font, float fontSizePt, float maxWidthPt) {
        String[] palabras = texto.trim().split("\\s+");
        if (palabras.length == 0 || (palabras.length == 1 && palabras[0].isEmpty())) {
            return 1;
        }

        float anchoEspacio = font.getWidth(" ", fontSizePt);
        int lineas = 1;
        float anchoLineaActual = 0f;

        for (String palabra : palabras) {
            float anchoPalabra = font.getWidth(palabra, fontSizePt);
            if (anchoLineaActual == 0f) {
                anchoLineaActual = anchoPalabra;
            } else if (anchoLineaActual + anchoEspacio + anchoPalabra <= maxWidthPt) {
                anchoLineaActual += anchoEspacio + anchoPalabra;
            } else {
                lineas++;
                anchoLineaActual = anchoPalabra;
            }
        }
        return lineas;
    }

    /**
     * Va bajando el tamaño de letra (de a medio punto) desde fontInicialPt hasta
     * que el texto entre en, como máximo, "maxLineas" líneas dentro de
     * "maxWidthPt". Nunca baja de fontMinimoPt: si ni siquiera al tamaño mínimo
     * entra en maxLineas, se deja el tamaño mínimo igual (el texto se recortará
     * visualmente en el peor de los casos, pero la generación del PDF nunca
     * falla ni se cae por esto).
     */
    private float ajustarFontParaAncho(String texto, PdfFont font, float fontInicialPt,
                                       float fontMinimoPt, float maxWidthPt, int maxLineas) {
        float fontSize = fontInicialPt;
        while (fontSize > fontMinimoPt) {
            if (contarLineas(texto, font, fontSize, maxWidthPt) <= maxLineas) {
                return fontSize;
            }
            fontSize -= 0.5f;
        }
        return fontMinimoPt;
    }
}