package com.sgi.auto.inventario;

import com.sgi.auto.compartido.EntidadBase;
import com.sgi.auto.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Producto extends EntidadBase {

    @Column(name = "codigo", nullable = false, length = 50)
    private String codigo;

    @Column(name = "numero_interno", length = 20)
    private String numeroInterno;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @Column(name = "unidad_medida", nullable = false, length = 30)
    @Builder.Default
    private String unidadMedida = "unidad";

    @Column(name = "precio_compra_con_iva", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioCompraConIva = BigDecimal.ZERO;

    @Column(name = "precio_compra_sin_iva", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioCompraSinIva = BigDecimal.ZERO;

    @Column(name = "precio_venta_detal", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioVentaDetal = BigDecimal.ZERO;

    @Column(name = "precio_venta_mayor", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal precioVentaMayor = BigDecimal.ZERO;

    @Column(name = "margen_ganancia_pct", insertable = false, updatable = false)
    private BigDecimal margenGananciaPct;

    @Column(name = "stock_actual", nullable = false)
    @Builder.Default
    private int stockActual = 0;

    @Column(name = "stock_minimo", nullable = false)
    @Builder.Default
    private int stockMinimo = 0;

    @Column(name = "mostrar_en_lista_precios", nullable = false)
    @Builder.Default
    private boolean mostrarEnListaPrecios = true;

    @Column(name = "esta_activo", nullable = false)
    @Builder.Default
    private boolean estaActivo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;
}