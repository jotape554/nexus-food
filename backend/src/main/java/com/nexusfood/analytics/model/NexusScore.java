package com.nexusfood.analytics.model;

import com.nexusfood.analytics.service.NexusScoreEngine.Situacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Nota do Nexus Score de um dia, com a versão da regra usada e cada indicador que a compôs —
 * qualquer nota exibida pode ser explicada ("por que 58?") e reproduzida.
 */
@Entity
@Table(name = "nexus_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NexusScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(nullable = false, length = 10)
    private String regraVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Situacao situacao;

    private Integer nota;

    @Column(length = 20)
    private String faixa;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal pesoValido;

    private int diasHistorico;

    @Column(name = "pedidos_concluidos_janela")
    private int pedidosConcluidosJanela;

    @Column(length = 300)
    private String motivoSemNota;

    @Column(nullable = false)
    private Instant calculadoEm;

    @OneToMany(mappedBy = "score", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<NexusScoreArea> areas = new ArrayList<>();

    @OneToMany(mappedBy = "score", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<NexusScoreIndicador> indicadores = new ArrayList<>();
}
