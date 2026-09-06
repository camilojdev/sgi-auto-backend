package com.sgi.auto.autenticacion;

import com.sgi.auto.usuarios.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "codigos_recuperacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "codigo", nullable = false, length = 6)
    private String codigo;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "usado", nullable = false)
    @Builder.Default
    private boolean usado = false;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}