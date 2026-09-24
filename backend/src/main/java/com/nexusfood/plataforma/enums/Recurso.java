package com.nexusfood.plataforma.enums;

import lombok.Getter;

/**
 * Catálogo central de "recursos" do Nexus Food e o plano mínimo que dá acesso a cada um.
 * Único lugar que precisa mudar para reclassificar um recurso entre planos.
 *
 * Cada controller de /api/** declara o recurso que usa com @RequerRecurso (ou @AcessoLivre);
 * quem aplica é o RecursoInterceptor. Limites quantitativos (usuários, histórico) ficam em PlanoSaas.
 */
@Getter
public enum Recurso {
    CARDAPIO(PlanoSaas.BASICO, "Cardápio digital"),
    PEDIDOS(PlanoSaas.BASICO, "Painel de pedidos"),
    CLIENTES(PlanoSaas.BASICO, "Clientes"),
    RELATORIOS(PlanoSaas.BASICO, "Relatórios de vendas"),
    EQUIPE(PlanoSaas.BASICO, "Usuários da equipe"),
    /** Nota, faixa, notas por área e evolução. */
    NEXUS_SCORE(PlanoSaas.PROFISSIONAL, "Nexus Score"),
    /** Indicador por indicador (valor, pontos, motivo) e as dicas automáticas. */
    NEXUS_DETALHES(PlanoSaas.PREMIUM, "Indicadores detalhados e dicas do Nexus");

    private final PlanoSaas planoMinimo;
    private final String nome;

    Recurso(PlanoSaas planoMinimo, String nome) {
        this.planoMinimo = planoMinimo;
        this.nome = nome;
    }
}
