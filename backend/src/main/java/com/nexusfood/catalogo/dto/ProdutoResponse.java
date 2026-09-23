package com.nexusfood.catalogo.dto;

import com.nexusfood.catalogo.model.Produto;

import java.math.BigDecimal;

public record ProdutoResponse(
        Long id,
        Long categoriaId,
        String nome,
        String descricao,
        BigDecimal preco,
        String imagemUrl,
        boolean disponivel,
        Integer ordem
) {
    public static ProdutoResponse de(Produto p) {
        return new ProdutoResponse(p.getId(), p.getCategoria().getId(), p.getNome(), p.getDescricao(),
                p.getPreco(), p.getImagemUrl(), p.isDisponivel(), p.getOrdem());
    }
}
