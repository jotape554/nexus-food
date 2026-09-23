package com.nexusfood.plataforma.model;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.enums.TipoTaxaEntrega;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** O tenant: toda informação do sistema pertence a exatamente um restaurante. */
@Entity
@Table(name = "restaurantes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurante {

    public static final String FUSO_PADRAO = "America/Sao_Paulo";
    public static final LocalTime VIRADA_PADRAO = LocalTime.of(4, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Usado na URL do cardápio público: /r/{slug} */
    @Column(nullable = false, unique = true)
    private String slug;

    private String telefone;
    private String endereco;
    private String logoUrl;

    /** Fuso usado para "hoje", dia operacional e horários exibidos (ex.: America/Sao_Paulo). */
    @Column(nullable = false)
    @Builder.Default
    private String fusoHorario = FUSO_PADRAO;

    /**
     * Horário em que o dia operacional vira. Com 04:00, um pedido às 00:40 de sábado conta
     * como sexta — é o que um restaurante que fecha de madrugada espera ver no relatório do dia.
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalTime horaViradaDia = VIRADA_PADRAO;

    /** Liga/desliga o recebimento de pedidos pelo cardápio público (loja aberta/fechada). */
    @Builder.Default
    private boolean aceitandoPedidos = false;

    @Builder.Default
    private boolean aceitaRetirada = true;
    @Builder.Default
    private boolean aceitaEntrega = false;
    @Builder.Default
    private boolean aceitaConsumoLocal = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TipoTaxaEntrega tipoTaxaEntrega = TipoTaxaEntrega.FIXA;

    /** Usada quando tipoTaxaEntrega = FIXA. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal taxaEntregaFixa = BigDecimal.ZERO;

    /** Valor mínimo dos itens (sem taxa) para aceitar um pedido; zero = sem mínimo. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal pedidoMinimo = BigDecimal.ZERO;

    /** Tempo que o restaurante promete para o pedido ficar pronto — base do indicador de pontualidade. */
    @Column(nullable = false)
    @Builder.Default
    private Integer tempoPreparoEstimadoMin = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlanoSaas planoSaas = PlanoSaas.BASICO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusAssinaturaSaas statusAssinaturaSaas = StatusAssinaturaSaas.TRIAL;

    /** Fim do período de teste gratuito. Após essa data, sem status ATIVA, o acesso ao painel é bloqueado. */
    private LocalDate dataFimTrial;

    /** Usados só para reconciliar eventos do webhook da Stripe — nunca saem para o cliente. */
    @JsonIgnore
    private String stripeCustomerId;
    @JsonIgnore
    private String stripeSubscriptionId;

    @Column(nullable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();
}
