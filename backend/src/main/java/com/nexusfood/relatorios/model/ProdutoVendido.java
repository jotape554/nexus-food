package com.nexusfood.relatorios.model;

import java.math.BigDecimal;

/** Vendas de um produto no período (só pedidos concluídos). O nome é o atual do cadastro. */
public record ProdutoVendido(Long produtoId, String nome, Long quantidade, BigDecimal faturamento) {}
