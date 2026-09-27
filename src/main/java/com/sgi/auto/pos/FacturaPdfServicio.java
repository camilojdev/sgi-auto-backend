package com.sgi.auto.pos;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sgi.auto.administracion.ConfiguracionNegocio;
import com.sgi.auto.administracion.ConfiguracionNegocioRepositorio;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import com.sgi.auto.inventario.BarcodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaPdfServicio {

    private final VentaRepositorio ventaRepositorio;
    private final ConfiguracionNegocioRepositorio configuracionNegocioRepositorio;
    private final BarcodeService barcodeService;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.of("es", "CO"));

    // ── Paleta corporativa ──
    private static final DeviceRgb NEGRO_CARBON = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb ROJO_CORPORATIVO = new DeviceRgb(0xE1, 0x06, 0x00);
    private static final DeviceRgb GRIS_CLARO = new DeviceRgb(0xF3, 0xF4, 0xF6);
    private static final DeviceRgb GRIS_BORDE = new DeviceRgb(0xD1, 0xD5, 0xDB);
    private static final DeviceRgb GRIS_TEXTO = new DeviceRgb(0x6B, 0x72, 0x80);
    private static final DeviceRgb ROJO_CLARO_FONDO = new DeviceRgb(0xFE, 0xE2, 0xE2);

    @Transactional(readOnly = true)
    public byte[] generar(Long ventaId) {
        Venta venta = ventaRepositorio.findById(ventaId)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró la venta con id: " + ventaId));

        ConfiguracionNegocio negocio = configuracionNegocioRepositorio.findById(1L).orElse(null);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(salida);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            doc.setMargins(0, 0, 14, 0);

            agregarEncabezado(doc, pdf, negocio, venta);
            agregarBarraContacto(doc, negocio);
            agregarBarraPrincipal(doc, venta);

            Div contenido = new Div().setPadding(11);
            if (venta.getEstado() == EstadoVenta.ANULADA) {
                agregarAvisoAnulacion(contenido, venta);
            }
            agregarTarjetaCliente(contenido, venta);
            agregarTablaItems(contenido, venta);
            agregarResumenLateral(contenido, venta);
            doc.add(contenido);

            agregarPiePagina(doc, negocio, venta);

        } catch (Exception e) {
            log.error("Error generando factura PDF de venta id={}: {}", ventaId, e.getMessage(), e);
            throw new RuntimeException("No se pudo generar la factura: " + e.getMessage());
        }

        return salida.toByteArray();
    }

    // ================= ENCABEZADO =================

    private void agregarEncabezado(Document doc, PdfDocument pdf, ConfiguracionNegocio negocio, Venta venta) {
        float altoEncabezado = 60f;

        Table encabezado = new Table(UnitValue.createPercentArray(new float[]{1, 2.2f}))
                .useAllAvailableWidth()
                .setMarginBottom(0);

        Cell celdaLogo = new Cell()
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(Border.NO_BORDER)
                .setBorderRight(new SolidBorder(ROJO_CORPORATIVO, 3))
                .setHeight(altoEncabezado)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER);

        if (negocio != null && esNoVacio(negocio.getLogoUrl())) {
            try {
                Image logo = new Image(ImageDataFactory.create(negocio.getLogoUrl()));
                logo.setWidth(38);
                logo.setHeight(38);
                logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
                celdaLogo.add(logo);
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo del negocio para la factura: {}", e.getMessage());
            }
        }
        encabezado.addCell(celdaLogo);

        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "Mi Negocio";
        String eslogan = negocio != null ? negocio.getEslogan() : null;

        Cell celdaTitulo = new Cell()
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(Border.NO_BORDER)
                .setHeight(altoEncabezado)
                .setPadding(0);

        Table interiorTitulo = new Table(UnitValue.createPercentArray(new float[]{2.1f, 1f}))
                .useAllAvailableWidth();

        Cell subCeldaTexto = new Cell()
                .setBorder(Border.NO_BORDER)
                .setHeight(altoEncabezado)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPaddingLeft(14);
        subCeldaTexto.add(new Paragraph(nombreNegocio).setBold().setFontSize(13).setFontColor(NEGRO_CARBON).setMargin(0));
        if (esNoVacio(eslogan)) {
            subCeldaTexto.add(new Paragraph(eslogan).setFontSize(8).setFontColor(GRIS_TEXTO).setMarginTop(1));
        }
        interiorTitulo.addCell(subCeldaTexto);

        Cell subCeldaBarcode = new Cell()
                .setBorder(Border.NO_BORDER)
                .setHeight(altoEncabezado)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingRight(12);
        try {
            Image codigoBarras = barcodeService.generarImagenCodigoBarras(pdf, venta.getCodigoSeguro());
            codigoBarras.setWidth(100);
            codigoBarras.setHeight(20);
            codigoBarras.setHorizontalAlignment(HorizontalAlignment.CENTER);
            subCeldaBarcode.add(codigoBarras);
            subCeldaBarcode.add(new Paragraph(venta.getCodigoSeguro())
                    .setFontSize(6f).setFontColor(GRIS_TEXTO)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(1));
        } catch (Exception e) {
            log.warn("No se pudo generar el código de barras de la venta id={}: {}", venta.getId(), e.getMessage());
        }
        interiorTitulo.addCell(subCeldaBarcode);

        celdaTitulo.add(interiorTitulo);
        encabezado.addCell(celdaTitulo);

        doc.add(encabezado);
        doc.add(crearLineaRoja(2f));
    }

    // ================= BARRA DE CONTACTO =================

    private void agregarBarraContacto(Document doc, ConfiguracionNegocio negocio) {
        List<String[]> bloques = new java.util.ArrayList<>();
        if (negocio != null && esNoVacio(negocio.getDireccion())) {
            bloques.add(new String[]{"D", "DIRECCIÓN", negocio.getDireccion()});
        }
        if (negocio != null && esNoVacio(negocio.getTelefono())) {
            bloques.add(new String[]{"T", "TELÉFONO", negocio.getTelefono()});
        }
        if (negocio != null && esNoVacio(negocio.getNit())) {
            bloques.add(new String[]{"N", "NIT", negocio.getNit()});
        }
        if (bloques.isEmpty()) return;

        float[] anchos = new float[bloques.size()];
        java.util.Arrays.fill(anchos, 1f);
        Table barra = new Table(UnitValue.createPercentArray(anchos)).useAllAvailableWidth();

        for (int i = 0; i < bloques.size(); i++) {
            String[] bloque = bloques.get(i);
            Cell celda = new Cell()
                    .setBackgroundColor(GRIS_CLARO)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(4);
            if (i < bloques.size() - 1) {
                celda.setBorderRight(new SolidBorder(GRIS_BORDE, 0.75f));
            }

            Table interior = new Table(UnitValue.createPercentArray(new float[]{1, 5})).useAllAvailableWidth();
            interior.addCell(crearInsigniaTexto(bloque[0]));
            Cell celdaTexto = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(5)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            celdaTexto.add(new Paragraph(bloque[1]).setFontSize(6.5f).setFontColor(GRIS_TEXTO).setBold().setMargin(0));
            celdaTexto.add(new Paragraph(bloque[2]).setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMargin(0));
            interior.addCell(celdaTexto);

            celda.add(interior);
            barra.addCell(celda);
        }

        doc.add(barra);
    }

    private Cell crearInsigniaTexto(String letra) {
        return new Cell()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setWidth(13).setHeight(13)
                .add(new Paragraph(letra).setFontColor(ColorConstants.WHITE).setBold().setFontSize(7.5f).setMargin(0));
    }

    // ================= BARRA PRINCIPAL =================

    private void agregarBarraPrincipal(Document doc, Venta venta) {
        Table barra = new Table(UnitValue.createPercentArray(new float[]{2.5f, 1, 1.5f}))
                .useAllAvailableWidth();

        Cell celdaTitulo = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(5)
                .add(new Paragraph("FACTURA DE VENTA")
                        .setFontColor(ColorConstants.WHITE).setBold().setFontSize(10).setMargin(0));
        barra.addCell(celdaTitulo);

        Cell celdaNumero = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(3)
                .add(crearCajaRoja(venta.getNumeroVenta(), 8));
        barra.addCell(celdaNumero);

        Cell celdaEstado = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(3)
                .add(crearCajaRoja(venta.getEstado() == EstadoVenta.ANULADA
                        ? "ANULADA" : "COMPLETADA", 7.5f));
        barra.addCell(celdaEstado);

        doc.add(barra);
    }

    private Div crearCajaRoja(String texto, float tamanoFuente) {
        Div caja = new Div()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setPadding(2)
                .setTextAlignment(TextAlignment.CENTER);
        caja.add(new Paragraph(texto).setFontColor(ColorConstants.WHITE).setBold().setFontSize(tamanoFuente).setMargin(0));
        return caja;
    }

    // ================= AVISO DE ANULACIÓN =================

    private void agregarAvisoAnulacion(Div contenido, Venta venta) {
        Div aviso = new Div()
                .setBackgroundColor(ROJO_CLARO_FONDO)
                .setBorder(new SolidBorder(ROJO_CORPORATIVO, 1f))
                .setPadding(8)
                .setMarginBottom(10);
        aviso.add(new Paragraph("⚠ Esta factura fue anulada")
                .setBold().setFontColor(ROJO_CORPORATIVO).setFontSize(9.5f).setMargin(0));
        if (esNoVacio(venta.getRazonAnulacion())) {
            aviso.add(new Paragraph("Motivo: " + venta.getRazonAnulacion())
                    .setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMarginTop(3));
        }
        contenido.add(aviso);
    }

    // ================= TARJETA CLIENTE =================

    private void agregarTarjetaCliente(Div contenido, Venta venta) {
        Table fila = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(8)
                .setKeepTogether(true);

        String nombreCliente = venta.getCliente() != null
                ? venta.getCliente().getNombreCompleto()
                : (esNoVacio(venta.getNombreClienteAnonimo()) ? venta.getNombreClienteAnonimo() : "Cliente general");

        Div tarjetaCliente = crearTarjeta("CLIENTE");
        Div cuerpoCliente = new Div().setPadding(6);
        cuerpoCliente.add(crearCampoEtiquetaValor("Nombre", nombreCliente));
        if (venta.getCliente() != null && esNoVacio(venta.getCliente().getNumeroIdentificacion())) {
            cuerpoCliente.add(crearCampoEtiquetaValor("Documento", venta.getCliente().getNumeroIdentificacion()));
        }
        tarjetaCliente.add(cuerpoCliente);

        Div tarjetaVenta = crearTarjeta("DETALLE DE PAGO");
        Div cuerpoVenta = new Div().setPadding(6);
        cuerpoVenta.add(crearCampoEtiquetaValor("Método de pago", venta.getMetodoPago().name()));
        cuerpoVenta.add(crearCampoEtiquetaValor("Fecha",
                venta.getCreadoEn() != null ? venta.getCreadoEn().format(FORMATO_FECHA) : "—"));
        tarjetaVenta.add(cuerpoVenta);

        fila.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(2).add(tarjetaCliente));
        fila.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(2).add(tarjetaVenta));

        contenido.add(fila);
    }

    private Div crearTarjeta(String titulo) {
        Div tarjeta = new Div()
                .setBorder(new SolidBorder(GRIS_BORDE, 0.75f))
                .setBackgroundColor(ColorConstants.WHITE);

        Div encabezado = new Div()
                .setBackgroundColor(NEGRO_CARBON)
                .setPadding(4)
                .add(new Paragraph(titulo).setFontColor(ColorConstants.WHITE).setBold().setFontSize(8.5f).setMargin(0));
        tarjeta.add(encabezado);
        return tarjeta;
    }

    private Div crearCampoEtiquetaValor(String etiqueta, String valor) {
        Div campo = new Div().setMarginBottom(3);
        campo.add(new Paragraph(etiqueta.toUpperCase()).setFontSize(6.5f).setFontColor(GRIS_TEXTO).setBold().setMargin(0));
        campo.add(new Paragraph(valor).setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMargin(0));
        return campo;
    }

    // ================= ITEMS =================

    private void agregarTablaItems(Div contenido, Venta venta) {
        contenido.add(crearEncabezadoSeccion("PRODUCTOS"));

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{4, 1.5f, 1, 1.5f, 1.5f}))
                .useAllAvailableWidth().setMarginBottom(6);

        agregarEncabezadoTablaOscuro(tabla,
                new String[]{"Producto", "Código", "Cantidad", "Precio", "Subtotal"},
                new TextAlignment[]{TextAlignment.LEFT, TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT, TextAlignment.RIGHT});

        for (ItemVenta item : venta.getItems()) {
            tabla.addCell(celdaFilaTabla(item.getNombreProductoSnapshot(), TextAlignment.LEFT));
            tabla.addCell(celdaFilaTabla(item.getCodigoProductoSnapshot(), TextAlignment.LEFT));
            tabla.addCell(celdaFilaTabla(String.valueOf(item.getCantidad()), TextAlignment.CENTER));
            tabla.addCell(celdaFilaTabla(formatearMoneda(item.getPrecioUnitarioCop()), TextAlignment.RIGHT));
            tabla.addCell(celdaFilaTabla(formatearMoneda(item.getSubtotalCop()), TextAlignment.RIGHT));
        }

        contenido.add(tabla);
    }

    private Paragraph crearEncabezadoSeccion(String texto) {
        return new Paragraph(texto)
                .setBold().setFontSize(9.5f).setFontColor(NEGRO_CARBON)
                .setMarginBottom(3)
                .setBorderBottom(new SolidBorder(ROJO_CORPORATIVO, 1.5f))
                .setPaddingBottom(2);
    }

    private void agregarEncabezadoTablaOscuro(Table tabla, String[] columnas, TextAlignment[] alineaciones) {
        for (int i = 0; i < columnas.length; i++) {
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph(columnas[i]).setFontColor(ColorConstants.WHITE).setBold().setFontSize(8).setMargin(0))
                    .setBackgroundColor(NEGRO_CARBON)
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(alineaciones[i])
                    .setPadding(4));
        }
    }

    private Cell celdaFilaTabla(String texto, TextAlignment alineacion) {
        return new Cell()
                .add(new Paragraph(texto != null ? texto : "").setFontSize(8.5f).setFontColor(NEGRO_CARBON))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRIS_BORDE, 0.5f))
                .setTextAlignment(alineacion)
                .setPadding(3.5f);
    }

    // ================= RESUMEN LATERAL =================

    private void agregarResumenLateral(Div contenido, Venta venta) {
        Table contenedor = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth()
                .setMarginBottom(8);

        contenedor.addCell(new Cell().setBorder(Border.NO_BORDER));

        Cell celdaResumen = new Cell().setBorder(Border.NO_BORDER);
        Div resumen = new Div()
                .setBorder(new SolidBorder(GRIS_BORDE, 0.75f))
                .setPadding(6)
                .setKeepTogether(true);

        resumen.add(filaResumen("Subtotal", formatearMoneda(venta.getSubtotalCop())));
        if (esPositivo(venta.getDescuentoCop())) {
            resumen.add(filaResumen("Descuento", "-" + formatearMoneda(venta.getDescuentoCop())));
        }
        if (esPositivo(venta.getPuntosCanjeadosCop())) {
            resumen.add(filaResumen("Descuento por puntos", "-" + formatearMoneda(venta.getPuntosCanjeadosCop())));
        }

        Div cajaTotal = new Div()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setPadding(5)
                .setMarginTop(3)
                .setTextAlignment(TextAlignment.CENTER);
        cajaTotal.add(new Paragraph("TOTAL")
                .setFontColor(ColorConstants.WHITE).setBold().setFontSize(8.5f).setMargin(0));
        cajaTotal.add(new Paragraph(formatearMoneda(venta.getTotalCop()))
                .setFontColor(ColorConstants.WHITE).setBold().setFontSize(14).setMarginTop(1));
        resumen.add(cajaTotal);

        if (esPositivo(venta.getVueltoCop())) {
            resumen.add(filaResumen("Vuelto", formatearMoneda(venta.getVueltoCop())).setMarginTop(4));
        }

        celdaResumen.add(resumen);
        contenedor.addCell(celdaResumen);
        contenido.add(contenedor);

        if (venta.getPuntosGanados() > 0) {
            Div puntos = new Div().setTextAlignment(TextAlignment.RIGHT).setMarginTop(4);
            puntos.add(new Paragraph("⭐ Puntos ganados: +" + venta.getPuntosGanados())
                    .setFontSize(8.5f).setFontColor(new DeviceRgb(0xB4, 0x78, 0x00)));
            contenido.add(puntos);
        }
    }

    private Div filaResumen(String etiqueta, String valor) {
        Table fila = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        fila.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f)
                .add(new Paragraph(etiqueta).setFontSize(8.5f).setFontColor(GRIS_TEXTO)));
        fila.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(valor).setFontSize(8.5f).setFontColor(NEGRO_CARBON)));
        Div contenedorFila = new Div();
        contenedorFila.add(fila);
        return contenedorFila;
    }

    // ================= PIE DE PÁGINA =================

    private void agregarPiePagina(Document doc, ConfiguracionNegocio negocio, Venta venta) {
        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "";
        String piePagina = negocio != null && esNoVacio(negocio.getPiePaginaFactura())
                ? negocio.getPiePaginaFactura()
                : "Gracias por su compra";

        doc.add(crearLineaRoja(1.5f).setMarginTop(4));

        Table pie = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
        pie.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f)
                .add(new Paragraph(piePagina + " — " + nombreNegocio)
                        .setFontSize(7).setFontColor(GRIS_TEXTO)));
        pie.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(venta.getNumeroVenta()).setFontSize(7).setFontColor(GRIS_TEXTO)));
        doc.add(pie);
    }

    // ================= UTILIDADES =================

    private Div crearLineaRoja(float grosor) {
        return new Div()
                .setHeight(grosor)
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setMargin(0);
    }

    private boolean esNoVacio(String texto) {
        return texto != null && !texto.isBlank();
    }

    private boolean esPositivo(BigDecimal monto) {
        return monto != null && monto.compareTo(BigDecimal.ZERO) > 0;
    }

    private String formatearMoneda(BigDecimal monto) {
        if (monto == null) monto = BigDecimal.ZERO;
        NumberFormat formato = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        formato.setMaximumFractionDigits(0);
        return formato.format(monto);
    }
}