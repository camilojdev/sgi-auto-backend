package com.sgi.auto.inventario;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "plantillas_etiqueta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlantillaEtiqueta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo; // HOJA | TERMICA

    @Column(name = "ancho_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal anchoMm;

    @Column(name = "alto_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal altoMm;

    @Column(name = "columnas", nullable = false)
    private int columnas;

    @Column(name = "filas", nullable = false)
    private int filas;

    @Column(name = "margen_superior_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal margenSuperiorMm;

    @Column(name = "margen_izquierdo_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal margenIzquierdoMm;

    @Column(name = "separacion_horizontal_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal separacionHorizontalMm;

    @Column(name = "separacion_vertical_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal separacionVerticalMm;

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();
}