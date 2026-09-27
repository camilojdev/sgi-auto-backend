package com.sgi.auto.taller;

import com.sgi.auto.clientes.Cliente;
import com.sgi.auto.compartido.EntidadBase;
import com.sgi.auto.pos.MetodoPago;
import com.sgi.auto.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordenes_trabajo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenDeTrabajo extends EntidadBase {

    // ── Identificadores públicos ──────────────────────
    // Número formato OT-000001.
    @Column(name = "numero_ot", insertable = false, updatable = false, length = 20)
    private String numeroOt;

    @Column(name = "codigo_seguro", nullable = false, unique = true, length = 20)
    private String codigoSeguro;

    // ── Datos del cliente ─────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "nombre_cliente", nullable = false, length = 200)
    private String nombreCliente;

    @Column(name = "celular_cliente", length = 20)
    private String celularCliente;

    // ── Datos del vehículo ───────────────────────────
    @Column(name = "placa_vehiculo", nullable = false, length = 10)
    private String placa;

    @Column(name = "marca_vehiculo", length = 60)
    private String marcaVehiculo;

    @Column(name = "modelo_vehiculo", length = 60)
    private String modeloVehiculo;

    @Column(name = "anio_vehiculo")
    private Integer anioVehiculo;

    @Column(name = "color_vehiculo", length = 40)
    private String colorVehiculo;

    @Column(name = "kilometraje")
    private Integer kilometraje;

    // ── Diagnóstico y observaciones ──────────────────
    @Column(name = "observaciones", nullable = false)
    private String descripcionProblema;

    @Column(name = "observaciones_mecanico")
    private String observacionesMecanico;

    @Column(name = "observaciones_entrega")
    private String observacionesEntrega;

    // ── Asignación ───────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mecanico_id")
    private Usuario mecanico;

    // ── Estado ───────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoOT estado = EstadoOT.RECIBIDO;

    // ── Tiempos ───────────────────────────────────────────────
    @Column(name = "fecha_prometida_entrega")
    private OffsetDateTime fechaPrometidaEntrega;

    @Column(name = "fecha_entrega_real")
    private OffsetDateTime fechaEntregaReal;

    // ── Pago ─────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "metodo_pago")
    private MetodoPago metodoPago;

    // ── Totales ────────────
    @Column(name = "total_servicios_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalServiciosCop = BigDecimal.ZERO;

    @Column(name = "total_repuestos_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRepuestosCop = BigDecimal.ZERO;

    @Column(name = "descuento_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuentoCop = BigDecimal.ZERO;

    @Column(name = "gran_total_cop", nullable = false, insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal granTotalCop;

    // ── Relaciones ────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @OneToMany(mappedBy = "ordenDeTrabajo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ServicioOT> servicios = new ArrayList<>();

    @OneToMany(mappedBy = "ordenDeTrabajo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RepuestoOT> repuestos = new ArrayList<>();

    @OneToMany(mappedBy = "ordenDeTrabajo", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FotoVehiculo> fotos = new ArrayList<>();

    // ── Método de negocio ─────────────────────────────────────
    public void recalcularTotales() {
        this.totalServiciosCop = servicios.stream()
                .map(ServicioOT::getSubtotalCop)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalRepuestosCop = repuestos.stream()
                .map(RepuestoOT::getSubtotalCop)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // granTotalCop lo calcula PostgreSQL automáticamente
    }
}