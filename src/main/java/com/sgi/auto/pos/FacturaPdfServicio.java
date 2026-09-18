package com.sgi.auto.pos;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sgi.auto.administracion.ConfiguracionNegocio;
import com.sgi.auto.administracion.ConfiguracionNegocioRepositorio;
import com.sgi.auto.compartido.RecursoNoEncontradoExcepcion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaPdfServicio {

    private final VentaRepositorio ventaRepositorio;
    private final ConfiguracionNegocioRepositorio configuracionNegocioRepositorio;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.of("es", "CO"));

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

            doc.setMargins(30, 36, 30, 36);

            agregarEncabezado(doc, negocio);
            agregarDatosVenta(doc, venta);
            agregarTablaItems(doc, venta);
            agregarTotales(doc, venta);
            agregarPiePagina(doc, negocio, venta);

        } catch (Exception e) {
            log.error("Error generando factura PDF de venta id={}: {}", ventaId, e.getMessage(), e);
            throw new RuntimeException("No se pudo generar la factura: " + e.getMessage());
        }

        return salida.toByteArray();
    }

    private void agregarEncabezado(Document doc, ConfiguracionNegocio negocio) {
        Table encabezado = new Table(UnitValue.createPercentArray(new float[]{1, 3}))
                .useAllAvailableWidth();

        Cell celdaLogo = new Cell().setBorder(Border.NO_BORDER);
        if (negocio != null && negocio.getLogoUrl() != null && !negocio.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(negocio.getLogoUrl()));
                logo.setWidth(60);
                logo.setHeight(60);
                celdaLogo.add(logo);
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo del negocio para la factura: {}", e.getMessage());
            }
        }
        encabezado.addCell(celdaLogo);

        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "Mi Negocio";
        String eslogan = negocio != null ? negocio.getEslogan() : null;

        Cell celdaTexto = new Cell().setBorder(Border.NO_BORDER);
        celdaTexto.add(new Paragraph(nombreNegocio).setBold().setFontSize(16));
        if (eslogan != null && !eslogan.isBlank()) {
            celdaTexto.add(new Paragraph(eslogan).setFontSize(10).setFontColor(ColorConstants.GRAY));
        }
        if (negocio != null && esNoVacio(negocio.getDireccion())) {
            celdaTexto.add(new Paragraph(negocio.getDireccion()).setFontSize(9).setFontColor(ColorConstants.GRAY));
        }
        if (negocio != null && esNoVacio(negocio.getTelefono())) {
            celdaTexto.add(new Paragraph("Tel: " + negocio.getTelefono()).setFontSize(9).setFontColor(ColorConstants.GRAY));
        }
        if (negocio != null && esNoVacio(negocio.getNit())) {
            celdaTexto.add(new Paragraph("NIT: " + negocio.getNit()).setFontSize(9).setFontColor(ColorConstants.GRAY));
        }
        encabezado.addCell(celdaTexto);

        doc.add(encabezado);
        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(8).setMarginBottom(12));
    }

    private void agregarDatosVenta(Document doc, Venta venta) {
        doc.add(new Paragraph("FACTURA DE VENTA #" + venta.getId())
                .setBold().setFontSize(13).setMarginBottom(6));

        String fecha = venta.getCreadoEn() != null ? venta.getCreadoEn().format(FORMATO_FECHA) : "";
        doc.add(new Paragraph("Fecha: " + fecha).setFontSize(10));

        String nombreCliente = venta.getCliente() != null
                ? venta.getCliente().getNombreCompleto()
                : (esNoVacio(venta.getNombreClienteAnonimo()) ? venta.getNombreClienteAnonimo() : "Cliente general");
        doc.add(new Paragraph("Cliente: " + nombreCliente).setFontSize(10));

        doc.add(new Paragraph("Método de pago: " + venta.getMetodoPago()).setFontSize(10));

        if (venta.getEstado() == EstadoVenta.ANULADA) {
            doc.add(new Paragraph("VENTA ANULADA")
                    .setBold().setFontColor(ColorConstants.RED).setFontSize(11).setMarginTop(4));
            if (esNoVacio(venta.getRazonAnulacion())) {
                doc.add(new Paragraph("Motivo: " + venta.getRazonAnulacion())
                        .setFontSize(9).setFontColor(ColorConstants.RED));
            }
        }

        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(10).setMarginBottom(10));
    }

    private void agregarTablaItems(Document doc, Venta venta) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{4, 1.5f, 1, 1.5f, 1.5f}))
                .useAllAvailableWidth();

        agregarCeldaEncabezado(tabla, "Producto");
        agregarCeldaEncabezado(tabla, "Cód.");
        agregarCeldaEncabezado(tabla, "Cant.");
        agregarCeldaEncabezado(tabla, "Precio");
        agregarCeldaEncabezado(tabla, "Subtotal");

        for (ItemVenta item : venta.getItems()) {
            tabla.addCell(celdaTexto(item.getNombreProductoSnapshot()));
            tabla.addCell(celdaTexto(item.getCodigoProductoSnapshot()));
            tabla.addCell(celdaTexto(String.valueOf(item.getCantidad())).setTextAlignment(TextAlignment.CENTER));
            tabla.addCell(celdaTexto(formatearMoneda(item.getPrecioUnitarioCop())).setTextAlignment(TextAlignment.RIGHT));
            tabla.addCell(celdaTexto(formatearMoneda(item.getSubtotalCop())).setTextAlignment(TextAlignment.RIGHT));
        }

        doc.add(tabla);
    }

    private void agregarCeldaEncabezado(Table tabla, String texto) {
        tabla.addHeaderCell(new Cell()
                .add(new Paragraph(texto).setBold().setFontSize(9))
                .setBackgroundColor(new DeviceRgb(240, 240, 240))
                .setBorder(Border.NO_BORDER)
                .setPadding(5));
    }

    private Cell celdaTexto(String texto) {
        return new Cell()
                .add(new Paragraph(texto != null ? texto : "").setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(5);
    }

    private void agregarTotales(Document doc, Venta venta) {
        doc.add(new Paragraph(" ").setMarginBottom(4));

        Table totales = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth();

        if (esPositivo(venta.getDescuentoCop())) {
            agregarFilaTotal(totales, "Descuento:", "-" + formatearMoneda(venta.getDescuentoCop()), false);
        }
        if (esPositivo(venta.getPuntosCanjeadosCop())) {
            agregarFilaTotal(totales, "Descuento por puntos:", "-" + formatearMoneda(venta.getPuntosCanjeadosCop()), false);
        }
        agregarFilaTotal(totales, "TOTAL:", formatearMoneda(venta.getTotalCop()), true);

        if (esPositivo(venta.getVueltoCop())) {
            agregarFilaTotal(totales, "Vuelto:", formatearMoneda(venta.getVueltoCop()), false);
        }

        doc.add(totales);

        if (venta.getPuntosGanados() > 0) {
            doc.add(new Paragraph("Puntos ganados: +" + venta.getPuntosGanados())
                    .setFontSize(10).setFontColor(new DeviceRgb(180, 120, 0)).setMarginTop(6));
        }
    }

    private void agregarFilaTotal(Table tabla, String etiqueta, String valor, boolean destacado) {
        Paragraph parrafoEtiqueta = new Paragraph(etiqueta).setFontSize(destacado ? 12 : 10);
        Paragraph parrafoValor = new Paragraph(valor).setFontSize(destacado ? 12 : 10);
        if (destacado) {
            parrafoEtiqueta.setBold();
            parrafoValor.setBold();
        }

        tabla.addCell(new Cell()
                .add(parrafoEtiqueta)
                .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
        tabla.addCell(new Cell()
                .add(parrafoValor)
                .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
    }

    private void agregarPiePagina(Document doc, ConfiguracionNegocio negocio, Venta venta) {
        doc.add(new Paragraph(" ").setMarginTop(20));

        String piePagina = negocio != null && esNoVacio(negocio.getPiePaginaFactura())
                ? negocio.getPiePaginaFactura()
                : "Gracias por su compra";
        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "";

        doc.add(new Paragraph(piePagina + " — " + nombreNegocio)
                .setFontSize(8).setFontColor(ColorConstants.GRAY));
        doc.add(new Paragraph("Factura #" + venta.getId())
                .setFontSize(8).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.RIGHT));
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