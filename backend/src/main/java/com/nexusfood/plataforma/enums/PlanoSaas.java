package com.nexusfood.plataforma.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plano de assinatura do RESTAURANTE dentro do Nexus Food. Os recursos de cada plano ficam
 * em {@link Recurso}; os limites quantitativos ficam aqui e são checados nos services.
 *
 * limiteUsuarios: usuários ATIVOS na equipe (null = sem limite).
 * Histórico dos relatórios: os últimos {@code historicoDias} dias, e também desde o dia 1 do mês
 * de {@code historicoMesesCheios} meses atrás (para "este mês" e "12 meses" funcionarem em qualquer
 * data). null = histórico completo.
 */
@Getter
public enum PlanoSaas {
    BASICO(new BigDecimal("69.90"), "Cardápio digital, painel de pedidos e relatórios dos últimos 30 dias", 2, 30, 0),
    PROFISSIONAL(new BigDecimal("129.90"), "Relatórios de 12 meses e Nexus Score", 5, 365, 11),
    PREMIUM(new BigDecimal("199.90"), "Histórico completo, indicadores detalhados e dicas do Nexus", null, null, null);

    private final BigDecimal precoMensal;
    private final String descricao;
    private final Integer limiteUsuarios;
    private final Integer historicoDias;
    private final Integer historicoMesesCheios;

    PlanoSaas(BigDecimal precoMensal, String descricao, Integer limiteUsuarios,
              Integer historicoDias, Integer historicoMesesCheios) {
        this.precoMensal = precoMensal;
        this.descricao = descricao;
        this.limiteUsuarios = limiteUsuarios;
        this.historicoDias = historicoDias;
        this.historicoMesesCheios = historicoMesesCheios;
    }

    /**
     * Os planos são cumulativos por ordem de declaração (BASICO < PROFISSIONAL < PREMIUM):
     * quem está num plano mais alto automaticamente atende os requisitos dos planos abaixo.
     */
    public boolean atendeNivelMinimo(PlanoSaas minimo) {
        return this.ordinal() >= minimo.ordinal();
    }

    /** Primeiro dia que os relatórios deste plano mostram, a partir do dia operacional de hoje. null = sem limite. */
    public LocalDate primeiroDiaDoHistorico(LocalDate hoje) {
        if (historicoDias == null) return null;
        LocalDate porDias = hoje.minusDays(historicoDias - 1L);
        LocalDate porMes = hoje.withDayOfMonth(1).minusMonths(historicoMesesCheios);
        return porDias.isBefore(porMes) ? porDias : porMes;
    }

    /** Menor plano cujo histórico alcança {@code dia} (usado para dizer "a partir do plano X"). */
    public static PlanoSaas menorPlanoComHistoricoDesde(LocalDate dia, LocalDate hoje) {
        for (PlanoSaas plano : values()) {
            LocalDate primeiro = plano.primeiroDiaDoHistorico(hoje);
            if (primeiro == null || !dia.isBefore(primeiro)) return plano;
        }
        return PREMIUM;
    }

    /** Menor plano que comporta {@code usuarios} usuários ativos. */
    public static PlanoSaas menorPlanoParaUsuarios(long usuarios) {
        for (PlanoSaas plano : values()) {
            if (plano.limiteUsuarios == null || usuarios <= plano.limiteUsuarios) return plano;
        }
        return PREMIUM;
    }
}
