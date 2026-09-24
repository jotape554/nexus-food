package com.nexusfood.catalogo.model;

import com.nexusfood.catalogo.enums.CobrancaGrupo;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Um grupo de escolhas de um produto: "Tamanho" (escolha 1), "Adicionais" (até 3)... */
@Entity
@Table(name = "grupos_opcoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoOpcoes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 80)
    private String nome;

    /** 0 = opcional; 1 ou mais = o cliente precisa escolher pelo menos isso. */
    @Column(nullable = false)
    private Integer minimo;

    @Column(nullable = false)
    private Integer maximo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CobrancaGrupo cobranca;

    @Column(nullable = false)
    private Integer ordem;

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC, id ASC")
    @Builder.Default
    private List<Opcao> opcoes = new ArrayList<>();

    public boolean obrigatorio() {
        return minimo != null && minimo > 0;
    }
}
