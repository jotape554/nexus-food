package com.nexusfood.catalogo.dto;

import com.nexusfood.plataforma.enums.TipoTaxaEntrega;
import com.nexusfood.plataforma.model.BairroEntrega;
import com.nexusfood.plataforma.model.Restaurante;

import java.math.BigDecimal;
import java.util.List;

/** Dados do restaurante que o cardápio público precisa — nada de plano, assinatura ou Stripe. */
public record RestaurantePublicoResponse(
        String nome,
        String slug,
        String telefone,
        String endereco,
        String logoUrl,
        boolean aceitandoPedidos,
        boolean aceitaRetirada,
        boolean aceitaEntrega,
        boolean aceitaConsumoLocal,
        TipoTaxaEntrega tipoTaxaEntrega,
        BigDecimal taxaEntregaFixa,
        BigDecimal pedidoMinimo,
        Integer tempoPreparoEstimadoMin,
        List<Bairro> bairros
) {
    public record Bairro(Long id, String nome, BigDecimal taxa) {}

    public static RestaurantePublicoResponse de(Restaurante r, List<BairroEntrega> bairrosAtivos) {
        List<Bairro> bairros = r.getTipoTaxaEntrega() == TipoTaxaEntrega.POR_BAIRRO
                ? bairrosAtivos.stream().map(b -> new Bairro(b.getId(), b.getNome(), b.getTaxa())).toList()
                : List.of();
        return new RestaurantePublicoResponse(r.getNome(), r.getSlug(), r.getTelefone(), r.getEndereco(), r.getLogoUrl(),
                r.isAceitandoPedidos(), r.isAceitaRetirada(), r.isAceitaEntrega(), r.isAceitaConsumoLocal(),
                r.getTipoTaxaEntrega(), r.getTaxaEntregaFixa(), r.getPedidoMinimo(), r.getTempoPreparoEstimadoMin(), bairros);
    }
}
