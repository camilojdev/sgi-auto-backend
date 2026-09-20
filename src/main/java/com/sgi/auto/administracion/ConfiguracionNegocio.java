package com.sgi.auto.administracion;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracion_negocio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionNegocio {

    @Id
    private Long id;

    @Column(name = "nombre_negocio", nullable = false, length = 150)
    private String nombreNegocio;

    @Column(name = "eslogan", length = 200)
    private String eslogan;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "logo_public_id", length = 200)
    private String logoPublicId;

    @Column(name = "direccion", length = 300)
    private String direccion;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "nit", length = 20)
    private String nit;

    @Column(name = "pie_pagina_factura", length = 500)
    private String piePaginaFactura;

    @Column(name = "actualizado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    @Column(name = "logo_etiquetas_url", columnDefinition = "TEXT")
    private String logoEtiquetasUrl;

    @Column(name = "logo_etiquetas_public_id", length = 200)
    private String logoEtiquetasPublicId;
}