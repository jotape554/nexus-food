package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;

/**
 * Resultado de escolher um plano. Sem assinatura ainda: {@code url} do checkout da Stripe.
 * Com assinatura ativa: a troca já foi feita na própria assinatura e {@code url} vem nulo.
 */
public record MudancaPlanoResponse(String url, PlanoSaas plano, String mensagem) {}
