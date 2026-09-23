package com.nexusfood.pedidos.model;

import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.plataforma.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Registro de auditoria de cada mudança de status: de onde, para onde, quando e quem. */
@Entity
@Table(name = "pedido_eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    private StatusPedido statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido statusNovo;

    @Column(nullable = false)
    private Instant ocorridoEm;

    /** Nulo quando a mudança veio do cliente (pedido criado pelo cardápio) ou do sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
}
