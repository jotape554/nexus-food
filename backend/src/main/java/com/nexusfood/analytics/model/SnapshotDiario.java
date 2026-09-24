package com.nexusfood.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Resumo de um dia operacional fechado, gravado pelo job do Nexus. Guarda somas e contagens
 * (nunca médias), para que dias possam ser somados em qualquer janela sem distorção.
 */
@Entity
@Table(name = "snapshots_diarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotDiario {

    /** Muda quando a forma de calcular o resumo mudar (o job recalcula os dias antigos). */
    public static final int VERSAO_CALCULO = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false)
    private LocalDate dia;

    private int pedidosRecebidos;
    private int pedidosConcluidos;
    private int canceladosRestaurante;
    private int canceladosCliente;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal faturamento;

    private int itensVendidos;
    private int clientesUnicos;
    private int clientesNovos;
    /** Pedidos concluídos com o horário "pronto" registrado (base da pontualidade). */
    private int pedidosComPronto;
    private int prontosNoPrazo;

    /** Pedidos concluídos por hora local, 24 números separados por vírgula (0h a 23h). */
    @Column(nullable = false, length = 200)
    private String pedidosPorHora;

    private int versaoCalculo;

    @Column(nullable = false)
    private Instant calculadoEm;
}
