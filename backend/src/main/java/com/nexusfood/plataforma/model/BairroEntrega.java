package com.nexusfood.plataforma.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Bairro atendido e a taxa de entrega dele — usado quando o restaurante cobra por bairro. */
@Entity
@Table(name = "bairros_entrega")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BairroEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxa;

    @Builder.Default
    private boolean ativo = true;
}
