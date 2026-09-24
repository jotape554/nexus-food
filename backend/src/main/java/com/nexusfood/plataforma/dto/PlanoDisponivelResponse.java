package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um plano com tudo o que a tela de comparação precisa: preço, limites e recursos incluídos.
 * A tela monta a tabela a partir daqui, então mudar um recurso de plano no backend já muda a tela.
 */
public record PlanoDisponivelResponse(
        PlanoSaas plano,
        String nome,
        BigDecimal precoMensal,
        String descricao,
        Integer limiteUsuarios,
        Integer historicoDias,
        List<String> recursos
) {}
