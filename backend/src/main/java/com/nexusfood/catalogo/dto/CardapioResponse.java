package com.nexusfood.catalogo.dto;

import java.util.List;

/** Cardápio público: só categorias ativas com pelo menos um produto ativo. */
public record CardapioResponse(List<CategoriaCardapio> categorias) {

    public record CategoriaCardapio(Long id, String nome, List<ProdutoResponse> produtos) {}
}
