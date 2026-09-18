package com.sgi.auto.pos;

import com.sgi.auto.clientes.Cliente;
import com.sgi.auto.compartido.EntidadBase;
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
@Table(name = "ventas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Venta extends EntidadBase {

    // Idempotencia offline
    @Column(name = "clave_idempotencia", unique = true, length = 36)
    private String claveIdempotencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "nombre_cliente_anonimo", length = 100)
    private String nombreClienteAnonimo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false)
    private EstadoVenta estado;

    // Totales
    @Column(name = "subtotal_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotalCop = BigDecimal.ZERO;

    @Column(name = "descuento_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal descuentoCop = BigDecimal.ZERO;

    @Column(name = "puntos_canjeados_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal puntosCanjeadosCop = BigDecimal.ZERO;

    @Column(name = "total_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalCop = BigDecimal.ZERO;

    @Column(name = "monto_efectivo_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoPagadoCop = BigDecimal.ZERO;

    @Column(name = "monto_transferencia_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoTransferenciaCop = BigDecimal.ZERO;

    @Column(name = "monto_credito_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal montoCreditoCop = BigDecimal.ZERO;

    @Column(name = "vuelto_entregado_cop", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal vueltoCop = BigDecimal.ZERO;

    // Puntos acumulados en esta venta
    @Column(name = "puntos_ganados", nullable = false)
    @Builder.Default
    private int puntosGanados = 0;

    // Razón de anulación
    @Column(name = "razon_anulacion")
    private String razonAnulacion;

    @Column(name = "anulada_en")
    private OffsetDateTime anuladaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemVenta> items = new ArrayList<>();
}