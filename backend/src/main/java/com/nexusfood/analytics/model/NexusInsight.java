package com.nexusfood.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Um insight gerado por modelo de texto: o texto guardado sempre corresponde a números conferíveis. */
@Entity
@Table(name = "nexus_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NexusInsight {

    public enum Severidade { ALERTA, OPORTUNIDADE, CONQUISTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(nullable = false, length = 40)
    private String template;

    private int templateVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severidade severidade;

    private int prioridade;

    /** Número principal do insight — decide se ele "piorou" e merece aparecer de novo. */
    @Column(precision = 14, scale = 4)
    private BigDecimal valorReferencia;

    /** Parâmetros do modelo de texto, em JSON. */
    @Column(nullable = false, length = 2000)
    private String parametros;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(nullable = false)
    private Instant criadoEm;
}
