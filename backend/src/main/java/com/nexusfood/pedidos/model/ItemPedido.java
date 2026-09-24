package com.nexusfood.pedidos.model;

import com.nexusfood.catalogo.model.Produto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Nome e preço são COPIADOS do produto no momento da venda: mudar o preço amanhã não pode
 * reescrever o faturamento de ontem.
 */
@Entity
@Table(name = "itens_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 160)
    private String nomeProduto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 300)
    private String observacao;

    /** Opções escolhidas (tamanho, borda, adicionais), já copiadas. precoUnitario inclui o que elas somam. */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ItemPedidoOpcao> opcoes = new ArrayList<>();

    public void adicionarOpcao(ItemPedidoOpcao opcao) {
        opcao.setItem(this);
        opcoes.add(opcao);
    }
}
