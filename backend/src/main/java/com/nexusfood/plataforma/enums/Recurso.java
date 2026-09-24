package com.nexusfood.plataforma.enums;

import lombok.Getter;

/**
 * Catálogo central de "recursos" do Nexus Food e o plano mínimo que dá acesso a cada um.
 * Único lugar que precisa mudar para reclassificar um recurso entre planos.
 *
 * A checagem de acesso vive em AssinaturaService.recursoLiberado(...); a aplicação prática
 * na API acontece em RecursoGateFilter.
 */
@Getter
public enum Recurso {
    CARDAPIO(PlanoSaas.BASICO),
    PEDIDOS(PlanoSaas.BASICO),
    CLIENTES(PlanoSaas.BASICO),
    // Na Fase 5 o Básico fica limitado ao histórico recente; por enquanto todos os planos acessam.
    RELATORIOS(PlanoSaas.BASICO);

    private final PlanoSaas planoMinimo;

    Recurso(PlanoSaas planoMinimo) {
        this.planoMinimo = planoMinimo;
    }
}
