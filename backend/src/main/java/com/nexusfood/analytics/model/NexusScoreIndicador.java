package com.nexusfood.analytics.model;

import com.nexusfood.analytics.service.NexusScoreEngine.StatusIndicador;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nexus_score_indicadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NexusScoreIndicador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "score_id", nullable = false)
    private NexusScore score;

    @Column(nullable = false, length = 20)
    private String area;

    @Column(nullable = false, length = 40)
    private String codigo;

    @Column(precision = 14, scale = 4)
    private BigDecimal valor;

    @Column(precision = 5, scale = 1)
    private BigDecimal pontos;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal pesoEfetivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusIndicador status;

    @Column(length = 300)
    private String motivo;

    private long amostra;
}
