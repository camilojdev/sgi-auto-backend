package com.sgi.auto.taller;

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
import java.util.Map;

/**
 * Genera la planilla de OT en PDF con identidad visual corporativa
 * (negro carbón + rojo).
 *
 * Optimización de espacio: se recorta al máximo el "peso fijo" (encabezado,
 * tarjetas, márgenes entre secciones) que no crece con el contenido, para
 * que quepan más filas de servicios/repuestos antes de necesitar una
 * segunda hoja. Las tarjetas superiores, el bloque de totales y las firmas
 * usan setKeepTogether para que, si de verdad hace falta una segunda
 * página, ningún bloque quede cortado a la mitad entre dos hojas.
 *
 * El identificador visible ("OT-000003") lo genera un trigger de la base
 * de datos y es solo para que las personas lo lean — es secuencial a
 * propósito, pero NUNCA se usa para buscar la OT por código de barras.
 * El código de barras (dentro del encabezado, junto al nombre del negocio)
 * codifica un identificador aparte (codigoSeguro), generado con
 * SecureRandom, para que escanear la planilla no permita deducir ni
 * adivinar el número de ninguna otra OT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanillaOtPdfServicio {

    private final OrdenDeTrabajoRepositorio ordenDeTrabajoRepositorio;
    private final ConfiguracionNegocioRepositorio configuracionNegocioRepositorio;
    private final BarcodeService barcodeService;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.of("es", "CO"));
    private static final DateTimeFormatter FORMATO_FECHA_CORTA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("es", "CO"));

    private static final Map<EstadoOT, String> ETIQUETAS_ESTADO = Map.of(
            EstadoOT.RECIBIDO, "Recibido",
            EstadoOT.EN_DIAGNOSTICO, "En diagnóstico",
            EstadoOT.EN_REPARACION, "En reparación",
            EstadoOT.ESPERANDO_REPUESTO, "Esperando repuesto",
            EstadoOT.LISTO, "Listo",
            EstadoOT.ENTREGADO, "Entregado",
            EstadoOT.CANCELADO, "Cancelado"
    );

    // ── Paleta corporativa ──
    private static final DeviceRgb NEGRO_CARBON = new DeviceRgb(0x11, 0x18, 0x27);
    private static final DeviceRgb ROJO_CORPORATIVO = new DeviceRgb(0xE1, 0x06, 0x00);
    private static final DeviceRgb GRIS_CLARO = new DeviceRgb(0xF3, 0xF4, 0xF6);
    private static final DeviceRgb GRIS_BORDE = new DeviceRgb(0xD1, 0xD5, 0xDB);
    private static final DeviceRgb GRIS_TEXTO = new DeviceRgb(0x6B, 0x72, 0x80);

    @Transactional(readOnly = true)
    public byte[] generar(Long otId) {
        OrdenDeTrabajo ot = ordenDeTrabajoRepositorio.findById(otId)
                .orElseThrow(() -> new RecursoNoEncontradoExcepcion(
                        "No se encontró la OT con id: " + otId));

        ConfiguracionNegocio negocio = configuracionNegocioRepositorio.findById(1L).orElse(null);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(salida);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            doc.setMargins(0, 0, 14, 0);

            agregarEncabezado(doc, pdf, negocio, ot);
            agregarBarraContacto(doc, negocio);
            agregarBarraPrincipalOt(doc, ot);

            Div contenido = new Div().setPadding(11);
            agregarTarjetasSuperiores(contenido, ot);
            agregarDescripcionProblema(contenido, ot);
            agregarTablaServicios(contenido, ot);
            agregarTablaRepuestos(contenido, ot);
            agregarResumenLateral(contenido, ot);
            agregarObservaciones(contenido, ot);
            agregarFirmas(contenido);
            doc.add(contenido);

            agregarPiePagina(doc, negocio, ot);

        } catch (Exception e) {
            log.error("Error generando planilla PDF de OT id={}: {}", otId, e.getMessage(), e);
            throw new RuntimeException("No se pudo generar la planilla: " + e.getMessage());
        }

        return salida.toByteArray();
    }

    // ================= ENCABEZADO =================

    private void agregarEncabezado(Document doc, PdfDocument pdf, ConfiguracionNegocio negocio, OrdenDeTrabajo ot) {
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
                log.warn("No se pudo cargar el logo del negocio para la planilla: {}", e.getMessage());
            }
        }
        encabezado.addCell(celdaLogo);

        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "Mi Negocio";
        String subtitulo = (negocio != null && esNoVacio(negocio.getEslogan()))
                ? negocio.getEslogan()
                : "Taller de Electricidad Automotriz";

        // Celda de título dividida en dos: texto (nombre + eslogan) a la
        // izquierda, y el código de barras a la derecha — aprovecha el
        // espacio en blanco que quedaba libre en vez de agregar una fila
        // nueva que restaría altura disponible al contenido.
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
        for (Paragraph linea : crearTituloConAcento(nombreNegocio)) {
            subCeldaTexto.add(linea);
        }
        subCeldaTexto.add(new Paragraph(subtitulo)
                .setFontColor(GRIS_TEXTO)
                .setFontSize(8)
                .setMarginTop(1));
        interiorTitulo.addCell(subCeldaTexto);

        Cell subCeldaBarcode = new Cell()
                .setBorder(Border.NO_BORDER)
                .setHeight(altoEncabezado)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingRight(12);
        try {
            Image codigoBarras = barcodeService.generarImagenCodigoBarras(pdf, ot.getCodigoSeguro());
            codigoBarras.setWidth(100);
            codigoBarras.setHeight(20);
            codigoBarras.setHorizontalAlignment(HorizontalAlignment.CENTER);
            subCeldaBarcode.add(codigoBarras);
            subCeldaBarcode.add(new Paragraph(ot.getCodigoSeguro())
                    .setFontSize(6f).setFontColor(GRIS_TEXTO)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(1));
        } catch (Exception e) {
            log.warn("No se pudo generar el código de barras de la OT id={}: {}", ot.getId(), e.getMessage());
        }
        interiorTitulo.addCell(subCeldaBarcode);

        celdaTitulo.add(interiorTitulo);
        encabezado.addCell(celdaTitulo);

        doc.add(encabezado);
        doc.add(crearLineaRoja(2f));
    }

    /**
     * Divide el nombre del negocio en dos líneas y resalta en rojo la última
     * palabra si es corta (una sigla, p. ej. "DB"). Si el nombre tiene pocas
     * palabras, se muestra en una sola línea sin dividir.
     */
    private List<Paragraph> crearTituloConAcento(String nombreNegocio) {
        String[] palabras = nombreNegocio.trim().split("\\s+");

        if (palabras.length <= 2) {
            return List.of(new Paragraph(nombreNegocio)
                    .setBold().setFontSize(13).setFontColor(NEGRO_CARBON).setMargin(0));
        }

        String ultimaPalabra = palabras[palabras.length - 1];
        boolean resaltarUltima = ultimaPalabra.length() <= 3;

        String primeraParte = String.join(" ",
                java.util.Arrays.copyOfRange(palabras, 0, palabras.length - 2));

        Paragraph linea1 = new Paragraph(primeraParte)
                .setBold().setFontSize(13).setFontColor(NEGRO_CARBON).setMargin(0);

        Paragraph linea2 = new Paragraph().setBold().setFontSize(13).setMargin(0);
        linea2.add(new Text(palabras[palabras.length - 2] + " ").setFontColor(NEGRO_CARBON));
        linea2.add(new Text(ultimaPalabra).setFontColor(resaltarUltima ? ROJO_CORPORATIVO : NEGRO_CARBON));

        return List.of(linea1, linea2);
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

    /** Insignia cuadrada roja con una letra blanca — usada como ícono simple y tipográficamente seguro. */
    private Cell crearInsigniaTexto(String letra) {
        return new Cell()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setWidth(13).setHeight(13)
                .add(new Paragraph(letra).setFontColor(ColorConstants.WHITE).setBold().setFontSize(7.5f).setMargin(0));
    }

    // ================= BARRA PRINCIPAL OT =================

    private void agregarBarraPrincipalOt(Document doc, OrdenDeTrabajo ot) {
        Table barra = new Table(UnitValue.createPercentArray(new float[]{2.5f, 1, 1.5f}))
                .useAllAvailableWidth();

        Cell celdaTitulo = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(5)
                .add(new Paragraph("ORDEN DE TRABAJO")
                        .setFontColor(ColorConstants.WHITE).setBold().setFontSize(10).setMargin(0));
        barra.addCell(celdaTitulo);

        // Número visible/humano (secuencial a propósito — no sirve para
        // buscar la OT por escaneo, solo es de lectura).
        Cell celdaNumero = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(3)
                .add(crearCajaRoja(ot.getNumeroOt(), 8));
        barra.addCell(celdaNumero);

        Cell celdaEstado = new Cell()
                .setBackgroundColor(NEGRO_CARBON)
                .setBorder(Border.NO_BORDER)
                .setHeight(22)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(3)
                .add(crearCajaRoja("ESTADO: " + ETIQUETAS_ESTADO
                        .getOrDefault(ot.getEstado(), ot.getEstado().name()).toUpperCase(), 7.5f));
        barra.addCell(celdaEstado);

        doc.add(barra);
    }

    /** Acento rojo discreto con texto blanco en negrita. */
    private Div crearCajaRoja(String texto, float tamanoFuente) {
        Div caja = new Div()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setPadding(2)
                .setTextAlignment(TextAlignment.CENTER);
        caja.add(new Paragraph(texto).setFontColor(ColorConstants.WHITE).setBold().setFontSize(tamanoFuente).setMargin(0));
        return caja;
    }

    // ================= TARJETAS SUPERIORES =================

    private void agregarTarjetasSuperiores(Div contenido, OrdenDeTrabajo ot) {
        Table fila = new Table(UnitValue.createPercentArray(new float[]{1, 1.3f, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(8)
                .setKeepTogether(true); // evita que las 3 tarjetas se partan entre dos páginas

        fila.addCell(envolverEnCelda(crearTarjetaCliente(ot)));
        fila.addCell(envolverEnCelda(crearTarjetaVehiculo(ot)));
        fila.addCell(envolverEnCelda(crearTarjetaFechas(ot)));

        contenido.add(fila);
    }

    private Cell envolverEnCelda(Div tarjeta) {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(2).add(tarjeta);
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

    private Div crearTarjetaCliente(OrdenDeTrabajo ot) {
        Div tarjeta = crearTarjeta("CLIENTE");
        Div cuerpo = new Div().setPadding(6);

        String documento = ot.getCliente() != null && esNoVacio(ot.getCliente().getNumeroIdentificacion())
                ? ot.getCliente().getNumeroIdentificacion() : "—";

        cuerpo.add(crearCampoEtiquetaValor("Nombre", ot.getNombreCliente()));
        cuerpo.add(crearCampoEtiquetaValor("Celular", valorOGuion(ot.getCelularCliente())));
        cuerpo.add(crearCampoEtiquetaValor("Documento", documento));
        tarjeta.add(cuerpo);
        return tarjeta;
    }

    private Div crearTarjetaVehiculo(OrdenDeTrabajo ot) {
        Div tarjeta = crearTarjeta("VEHÍCULO");
        Div cuerpo = new Div().setPadding(6);

        Div cajaPlaca = new Div()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setPadding(3)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4);
        cajaPlaca.add(new Paragraph(ot.getPlaca())
                .setFontColor(ColorConstants.WHITE).setBold().setFontSize(12).setMargin(0));
        cuerpo.add(cajaPlaca);

        Table datos = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        datos.addCell(celdaCampoSinBorde("Marca", valorOGuion(ot.getMarcaVehiculo())));
        datos.addCell(celdaCampoSinBorde("Modelo", valorOGuion(ot.getModeloVehiculo())));
        datos.addCell(celdaCampoSinBorde("Año", ot.getAnioVehiculo() != null ? String.valueOf(ot.getAnioVehiculo()) : "—"));
        datos.addCell(celdaCampoSinBorde("Color", valorOGuion(ot.getColorVehiculo())));
        datos.addCell(celdaCampoSinBorde("Kilometraje", ot.getKilometraje() != null ? ot.getKilometraje() + " km" : "—"));
        datos.addCell(celdaCampoSinBorde("Mecánico",
                ot.getMecanico() != null ? ot.getMecanico().getNombreCompleto() : "No asignado"));
        cuerpo.add(datos);

        tarjeta.add(cuerpo);
        return tarjeta;
    }

    private Div crearTarjetaFechas(OrdenDeTrabajo ot) {
        Div tarjeta = crearTarjeta("FECHAS");
        Div cuerpo = new Div().setPadding(6);

        cuerpo.add(crearCampoEtiquetaValor("Ingreso",
                ot.getCreadoEn() != null ? ot.getCreadoEn().format(FORMATO_FECHA) : "—"));
        cuerpo.add(crearCampoEtiquetaValor("Entrega prometida",
                ot.getFechaPrometidaEntrega() != null ? ot.getFechaPrometidaEntrega().format(FORMATO_FECHA_CORTA) : "—"));
        if (ot.getFechaEntregaReal() != null) {
            cuerpo.add(crearCampoEtiquetaValor("Entrega real", ot.getFechaEntregaReal().format(FORMATO_FECHA)));
        }

        tarjeta.add(cuerpo);
        return tarjeta;
    }

    private Div crearCampoEtiquetaValor(String etiqueta, String valor) {
        Div campo = new Div().setMarginBottom(3);
        campo.add(new Paragraph(etiqueta.toUpperCase()).setFontSize(6.5f).setFontColor(GRIS_TEXTO).setBold().setMargin(0));
        campo.add(new Paragraph(valor).setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMargin(0));
        return campo;
    }

    private Cell celdaCampoSinBorde(String etiqueta, String valor) {
        Cell celda = new Cell().setBorder(Border.NO_BORDER).setPaddingBottom(3);
        celda.add(new Paragraph(etiqueta.toUpperCase()).setFontSize(6.5f).setFontColor(GRIS_TEXTO).setBold().setMargin(0));
        celda.add(new Paragraph(valor).setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMargin(0));
        return celda;
    }

    // ================= DESCRIPCIÓN DEL PROBLEMA =================

    private void agregarDescripcionProblema(Div contenido, OrdenDeTrabajo ot) {
        Div tarjeta = crearTarjeta("DESCRIPCIÓN DEL PROBLEMA");
        Div cuerpo = new Div().setPadding(6);
        cuerpo.add(new Paragraph(esNoVacio(ot.getDescripcionProblema()) ? ot.getDescripcionProblema() : "Sin descripción registrada.")
                .setFontSize(9).setFontColor(NEGRO_CARBON).setMultipliedLeading(1.2f));
        tarjeta.add(cuerpo);
        tarjeta.setMarginBottom(8);
        contenido.add(tarjeta);
    }

    // ================= SERVICIOS =================

    private void agregarTablaServicios(Div contenido, OrdenDeTrabajo ot) {
        contenido.add(crearEncabezadoSeccion("SERVICIOS"));

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{5, 1.2f, 1.8f, 1.8f}))
                .useAllAvailableWidth().setMarginBottom(6);

        agregarEncabezadoTablaOscuro(tabla,
                new String[]{"Descripción", "Cantidad", "Valor unitario", "Subtotal"},
                new TextAlignment[]{TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT, TextAlignment.RIGHT});

        List<ServicioOT> servicios = ot.getServicios();
        if (servicios == null || servicios.isEmpty()) {
            tabla.addCell(celdaVaciaTabla(4, "Sin servicios registrados"));
        } else {
            for (ServicioOT s : servicios) {
                tabla.addCell(celdaFilaTabla(s.getDescripcion(), TextAlignment.LEFT));
                tabla.addCell(celdaFilaTabla(String.valueOf(s.getCantidad()), TextAlignment.CENTER));
                tabla.addCell(celdaFilaTabla(formatearMoneda(s.getPrecioUnitarioCop()), TextAlignment.RIGHT));
                tabla.addCell(celdaFilaTabla(formatearMoneda(s.getSubtotalCop()), TextAlignment.RIGHT));
            }
        }
        contenido.add(tabla);
    }

    // ================= REPUESTOS =================

    private void agregarTablaRepuestos(Div contenido, OrdenDeTrabajo ot) {
        contenido.add(crearEncabezadoSeccion("REPUESTOS"));

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{1.3f, 3.7f, 1.2f, 1.8f, 1.8f}))
                .useAllAvailableWidth().setMarginBottom(6);

        agregarEncabezadoTablaOscuro(tabla,
                new String[]{"Código", "Repuesto", "Cantidad", "Valor unitario", "Subtotal"},
                new TextAlignment[]{TextAlignment.LEFT, TextAlignment.LEFT, TextAlignment.CENTER, TextAlignment.RIGHT, TextAlignment.RIGHT});

        List<RepuestoOT> repuestos = ot.getRepuestos();
        if (repuestos == null || repuestos.isEmpty()) {
            tabla.addCell(celdaVaciaTabla(5, "Sin repuestos registrados"));
        } else {
            for (RepuestoOT r : repuestos) {
                String codigo = r.getProducto() != null && esNoVacio(r.getProducto().getCodigo())
                        ? r.getProducto().getCodigo() : "—";
                tabla.addCell(celdaFilaTabla(codigo, TextAlignment.LEFT));
                tabla.addCell(celdaFilaTabla(r.getNombreRepuestoSnapshot(), TextAlignment.LEFT));
                tabla.addCell(celdaFilaTabla(String.valueOf(r.getCantidad()), TextAlignment.CENTER));
                tabla.addCell(celdaFilaTabla(formatearMoneda(r.getPrecioUnitarioCop()), TextAlignment.RIGHT));
                tabla.addCell(celdaFilaTabla(formatearMoneda(r.getSubtotalCop()), TextAlignment.RIGHT));
            }
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

    /** El encabezado de cada columna usa la MISMA alineación que sus datos,
     *  para que el valor quede justo debajo de su título. */
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

    private Cell celdaVaciaTabla(int colSpan, String mensaje) {
        return new Cell(1, colSpan)
                .add(new Paragraph(mensaje).setFontSize(8.5f).setFontColor(GRIS_TEXTO).setItalic())
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(GRIS_BORDE, 0.5f))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(6);
    }

    // ================= RESUMEN LATERAL =================

    private void agregarResumenLateral(Div contenido, OrdenDeTrabajo ot) {
        Table contenedor = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth()
                .setMarginBottom(8);

        contenedor.addCell(new Cell().setBorder(Border.NO_BORDER));

        Cell celdaResumen = new Cell().setBorder(Border.NO_BORDER);
        Div resumen = new Div()
                .setBorder(new SolidBorder(GRIS_BORDE, 0.75f))
                .setPadding(6)
                .setKeepTogether(true); // el bloque de totales nunca se parte entre dos páginas

        resumen.add(filaResumen("Total servicios", formatearMoneda(ot.getTotalServiciosCop())));
        resumen.add(filaResumen("Total repuestos", formatearMoneda(ot.getTotalRepuestosCop())));
        if (esPositivo(ot.getDescuentoCop())) {
            resumen.add(filaResumen("Descuento", "-" + formatearMoneda(ot.getDescuentoCop())));
        }
        if (ot.getMetodoPago() != null) {
            resumen.add(filaResumen("Método de pago", ot.getMetodoPago().name()));
        }

        Div cajaTotal = new Div()
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setPadding(5)
                .setMarginTop(3)
                .setTextAlignment(TextAlignment.CENTER);
        cajaTotal.add(new Paragraph("TOTAL GENERAL")
                .setFontColor(ColorConstants.WHITE).setBold().setFontSize(8.5f).setMargin(0));
        cajaTotal.add(new Paragraph(formatearMoneda(ot.getGranTotalCop()))
                .setFontColor(ColorConstants.WHITE).setBold().setFontSize(14).setMarginTop(1));
        resumen.add(cajaTotal);

        celdaResumen.add(resumen);
        contenedor.addCell(celdaResumen);

        contenido.add(contenedor);
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

    // ================= OBSERVACIONES =================

    private void agregarObservaciones(Div contenido, OrdenDeTrabajo ot) {
        boolean tieneObservaciones = esNoVacio(ot.getObservacionesMecanico()) || esNoVacio(ot.getObservacionesEntrega());
        if (!tieneObservaciones) return;

        Div tarjeta = crearTarjeta("OBSERVACIONES");
        Div cuerpo = new Div().setPadding(6);

        if (esNoVacio(ot.getObservacionesMecanico())) {
            cuerpo.add(new Paragraph("Del mecánico").setBold().setFontSize(7.5f).setFontColor(GRIS_TEXTO).setMarginBottom(1));
            cuerpo.add(new Paragraph(ot.getObservacionesMecanico()).setFontSize(8.5f).setFontColor(NEGRO_CARBON).setMarginBottom(4));
        }
        if (esNoVacio(ot.getObservacionesEntrega())) {
            cuerpo.add(new Paragraph("De entrega").setBold().setFontSize(7.5f).setFontColor(GRIS_TEXTO).setMarginBottom(1));
            cuerpo.add(new Paragraph(ot.getObservacionesEntrega()).setFontSize(8.5f).setFontColor(NEGRO_CARBON));
        }

        tarjeta.add(cuerpo);
        tarjeta.setMarginBottom(8);
        contenido.add(tarjeta);
    }

    // ================= FIRMAS =================

    private void agregarFirmas(Div contenido) {
        Table firmas = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .useAllAvailableWidth().setMarginTop(10).setKeepTogether(true);

        firmas.addCell(celdaFirma("Cliente"));
        firmas.addCell(celdaFirma("Mecánico"));
        firmas.addCell(celdaFirma("Recibido por"));

        contenido.add(firmas);
    }

    private Cell celdaFirma(String etiqueta) {
        Cell celda = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER).setPadding(3);
        celda.add(new Paragraph(" ")
                .setBorderTop(new SolidBorder(GRIS_BORDE, 1f))
                .setMarginTop(12).setMarginBottom(2));
        celda.add(new Paragraph(etiqueta).setFontSize(7.5f).setFontColor(GRIS_TEXTO));
        return celda;
    }

    // ================= PIE DE PÁGINA =================

    private void agregarPiePagina(Document doc, ConfiguracionNegocio negocio, OrdenDeTrabajo ot) {
        String nombreNegocio = negocio != null ? negocio.getNombreNegocio() : "";

        doc.add(crearLineaRoja(1.5f).setMarginTop(4));

        Table pie = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
        pie.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f)
                .add(new Paragraph("Documento generado por " + nombreNegocio)
                        .setFontSize(7).setFontColor(GRIS_TEXTO)));
        pie.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(1.5f).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(ot.getNumeroOt()).setFontSize(7).setFontColor(GRIS_TEXTO)));
        doc.add(pie);
    }

    // ================= UTILIDADES =================

    private Div crearLineaRoja(float grosor) {
        return new Div()
                .setHeight(grosor)
                .setBackgroundColor(ROJO_CORPORATIVO)
                .setMargin(0);
    }

    private String valorOGuion(String valor) {
        return esNoVacio(valor) ? valor : "—";
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