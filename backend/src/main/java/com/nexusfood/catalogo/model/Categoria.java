package com.nexusfood.catalogo.model;

import com.nexusfood.plataforma.model.Restaurante;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Posição da categoria no cardápio (menor aparece primeiro). */
    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @Builder.Default
    private boolean ativa = true;
}
