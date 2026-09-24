package com.nexusfood.pedidos.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Opção escolhida num item, copiada no momento da venda (como nome e preço do produto). */
@Entity
@Table(name = "itens_pedido_opcoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedidoOpcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemPedido item;

    /** Referência solta: a opção pode ser removida do cardápio depois. */
    private Long opcaoId;

    @Column(nullable = false, length = 80)
    private String nomeGrupo;

    @Column(nullable = false, length = 80)
    private String nomeOpcao;

    /** Preço de tabela da opção na hora do pedido (o que ela somou depende da cobrança do grupo). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;
}
