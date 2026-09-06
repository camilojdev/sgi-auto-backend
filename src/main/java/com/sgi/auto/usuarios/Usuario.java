package com.sgi.auto.usuarios;

import com.sgi.auto.compartido.EntidadBase;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;


@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends EntidadBase implements UserDetails {

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "nombre_usuario", nullable = false, length = 60)
    private String nombreUsuario;

    @Column(name = "correo", nullable = false, length = 150)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name= "rol", nullable = false)
    private RolUsuario rol;

    // ── Permisos granulares para CAJERA ──────────────────────────
    @Column(name = "puede_aplicar_descuento", nullable = false)
    @Builder.Default
    private boolean puedeAplicarDescuento = false;

    @Column(name = "puede_anular_venta", nullable = false)
    @Builder.Default
    private boolean puedeAnularVenta = false;

    @Column(name = "puede_cerrar_caja", nullable = false)
    @Builder.Default
    private boolean puedeCerrarCaja = false;

    @Column(name = "puede_ver_reportes", nullable = false)
    @Builder.Default
    private boolean puedeVerReportes = false;

    @Column(name = "puede_gestionar_credito", nullable = false)
    @Builder.Default
    private boolean puedeGestionarCredito = false;

    // ── Control de acceso ─────────────────────────────────────────
    @Column(name = "esta_activo", nullable = false)
    @Builder.Default
    private boolean estaActivo = true;

    @Column(name = "intentos_fallidos_login", nullable = false)
    @Builder.Default
    private int intentosFallidosLogin = 0;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @Column(name = "ultimo_ingreso_en")
    private OffsetDateTime ultimoIngresoEn;

    // ── UserDetails — Spring Security ─────────────────────────────

    @Override
    public String getUsername() {
        return nombreUsuario;
    }

    @Override
    public String getPassword() {
        return contrasenaHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public boolean isAccountNonExpired()  { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        if (bloqueadoHasta == null) return true;
        return OffsetDateTime.now().isAfter(bloqueadoHasta);
    }

    @Override
    public boolean isEnabled() { return estaActivo; }

    // ── Métodos de negocio ────────────────────────────────────────

    public boolean estaBloqueado() {
        return bloqueadoHasta != null
                && OffsetDateTime.now().isBefore(bloqueadoHasta);
    }

    public void registrarIntentoFallido() {
        this.intentosFallidosLogin++;
        if (this.intentosFallidosLogin >= 5) {
            this.bloqueadoHasta = OffsetDateTime.now().plusMinutes(15);
        }
    }

    public void registrarIngresoExitoso() {
        this.intentosFallidosLogin = 0;
        this.bloqueadoHasta = null;
        this.ultimoIngresoEn = OffsetDateTime.now();
    }
}
