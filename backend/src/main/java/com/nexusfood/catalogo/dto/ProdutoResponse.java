package com.nexusfood.catalogo.dto;

import com.nexusfood.catalogo.enums.CobrancaGrupo;
import com.nexusfood.catalogo.model.GrupoOpcoes;
import com.nexusfood.catalogo.model.Opcao;
import com.nexusfood.catalogo.model.Produto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produto com os grupos de opções. precoMinimo: o menor preço possível escolhendo o mínimo
 * obrigatório de cada grupo (é o "a partir de" do cardápio; igual a preco sem grupo obrigatório).
 */
public record ProdutoResponse(
        Long id,
        Long categoriaId,
        String nome,
        String descricao,
        BigDecimal preco,
        BigDecimal precoMinimo,
        String imagemUrl,
        boolean disponivel,
        Integer ordem,
        List<Grupo> grupos
) {
    public record Grupo(Long id, String nome, int minimo, int maximo, CobrancaGrupo cobranca, List<OpcaoItem> opcoes) {
        static Grupo de(GrupoOpcoes g) {
            return new Grupo(g.getId(), g.getNome(), g.getMinimo(), g.getMaximo(), g.getCobranca(),
                    g.getOpcoes().stream().map(OpcaoItem::de).toList());
        }
    }

    public record OpcaoItem(Long id, String nome, BigDecimal preco, boolean disponivel) {
        static OpcaoItem de(Opcao o) {
            return new OpcaoItem(o.getId(), o.getNome(), o.getPreco(), o.isDisponivel());
        }
    }

    public static ProdutoResponse de(Produto p) {
        return new ProdutoResponse(p.getId(), p.getCategoria().getId(), p.getNome(), p.getDescricao(),
                p.getPreco(), precoMinimo(p), p.getImagemUrl(), p.isDisponivel(), p.getOrdem(),
                p.getGrupos().stream().map(Grupo::de).toList());
    }

    /** Preço base + o mais barato que dá para pagar em cada grupo obrigatório (com as disponíveis). */
    static BigDecimal precoMinimo(Produto p) {
        BigDecimal total = p.getPreco();
        for (GrupoOpcoes g : p.getGrupos()) {
            if (!g.obrigatorio()) continue;
            List<BigDecimal> precos = g.getOpcoes().stream().filter(Opcao::isDisponivel)
                    .map(Opcao::getPreco).sorted().limit(g.getMinimo()).toList();
            if (precos.size() < g.getMinimo()) continue;
            total = total.add(g.getCobranca().valor(precos));
        }
        return total;
    }
}
