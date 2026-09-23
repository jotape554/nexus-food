package com.nexusfood.plataforma.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Plano de assinatura do RESTAURANTE dentro do Nexus Food. Os recursos de cada plano ficam
 * em {@link Recurso}; os limites quantitativos (usuários, histórico de relatório) entram
 * aqui na Fase 5.
 */
@Getter
public enum PlanoSaas {
    BASICO(new BigDecimal("69.90"), "Cardápio digital, painel de pedidos e relatório do dia"),
    PROFISSIONAL(new BigDecimal("129.90"), "Relatórios de 12 meses e Nexus Score"),
    PREMIUM(new BigDecimal("199.90"), "Histórico completo, indicadores detalhados e insights");

    private final BigDecimal precoMensal;
    private final String descricao;

    PlanoSaas(BigDecimal precoMensal, String descricao) {
        this.precoMensal = precoMensal;
        this.descricao = descricao;
    }

    /**
     * Os planos são cumulativos por ordem de declaração (BASICO < PROFISSIONAL < PREMIUM):
     * quem está num plano mais alto automaticamente atende os requisitos dos planos abaixo.
     */
    public boolean atendeNivelMinimo(PlanoSaas minimo) {
        return this.ordinal() >= minimo.ordinal();
    }
}
