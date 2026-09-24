package com.nexusfood.catalogo.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Uma escolha dentro do grupo ("Grande", "Borda de catupiry", "Bacon"), com o preço que ela soma. */
@Entity
@Table(name = "opcoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoOpcoes grupo;

    @Column(nullable = false, length = 80)
    private String nome;

    /** Quanto soma ao preço do produto (0 = sem custo). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    /** Falso = esgotada: aparece no cardápio, mas não pode ser escolhida. */
    @Builder.Default
    private boolean disponivel = true;

    @Column(nullable = false)
    private Integer ordem;
}
