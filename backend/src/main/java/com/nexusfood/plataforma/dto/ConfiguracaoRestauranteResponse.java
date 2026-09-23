package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.TipoTaxaEntrega;
import com.nexusfood.plataforma.model.Restaurante;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracaoRestauranteResponse(
        Long id,
        String nome,
        String slug,
        String telefone,
        String endereco,
        String logoUrl,
        String fusoHorario,
        LocalTime horaViradaDia,
        boolean aceitandoPedidos,
        boolean aceitaRetirada,
        boolean aceitaEntrega,
        boolean aceitaConsumoLocal,
        TipoTaxaEntrega tipoTaxaEntrega,
        BigDecimal taxaEntregaFixa,
        BigDecimal pedidoMinimo,
        Integer tempoPreparoEstimadoMin
) {
    public static ConfiguracaoRestauranteResponse de(Restaurante r) {
        return new ConfiguracaoRestauranteResponse(r.getId(), r.getNome(), r.getSlug(), r.getTelefone(),
                r.getEndereco(), r.getLogoUrl(), r.getFusoHorario(), r.getHoraViradaDia(), r.isAceitandoPedidos(),
                r.isAceitaRetirada(), r.isAceitaEntrega(), r.isAceitaConsumoLocal(), r.getTipoTaxaEntrega(),
                r.getTaxaEntregaFixa(), r.getPedidoMinimo(), r.getTempoPreparoEstimadoMin());
    }
}
