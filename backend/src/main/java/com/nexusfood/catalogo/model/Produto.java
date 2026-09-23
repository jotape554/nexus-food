package com.nexusfood.catalogo.model;

import com.nexusfood.plataforma.model.Restaurante;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(length = 1000)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(length = 500)
    private String imagemUrl;

    /** Falso = "esgotado": continua no cardápio, mas não pode ser pedido. */
    @Builder.Default
    private boolean disponivel = true;

    /**
     * Falso = removido do cardápio. Produto nunca é apagado de verdade: pedidos antigos e os
     * indicadores de "produto parado" continuam precisando dele.
     */
    @Builder.Default
    private boolean ativo = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @Column(nullable = false)
    private Instant criadoEm;
}
