package com.nexusfood.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nexus_score_areas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NexusScoreArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "score_id", nullable = false)
    private NexusScore score;

    @Column(nullable = false, length = 20)
    private String area;

    @Column(precision = 5, scale = 1)
    private BigDecimal nota;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal peso;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal pesoValido;

    private boolean exibida;
}
