package com.nexusfood.relatorios.dto;

import com.nexusfood.relatorios.model.Agrupamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Relatório de vendas de um período. Valores em R$; percentuais com uma casa (ex.: 12.5 = 12,5%). */
public record RelatorioVendasResponse(
        /** "Hoje" no dia operacional do restaurante — base dos atalhos de período da tela. */
        LocalDate hoje,
        /** Primeiro dia que o plano atual deixa consultar (null = histórico completo). */
        LocalDate primeiroDiaPermitido,
        LocalDate inicio,
        LocalDate fim,
        Agrupamento agrupamento,
        Resumo resumo,
        Comparacao comparacao,
        List<PontoSerie> serie,
        List<Fatia> porModalidade,
        List<Fatia> porFormaPagamento,
        List<Faixa> porHora,
        List<Faixa> porDiaDaSemana,
        List<Produto> produtos
) {
    public record Resumo(
            BigDecimal faturamento,
            long pedidosConcluidos,
            long pedidosRecebidos,
            BigDecimal ticketMedio,
            long cancelados,
            long canceladosPeloRestaurante,
            BigDecimal taxaCancelamento,
            long itensVendidos,
            long clientesUnicos,
            long clientesNovos,
            long clientesRecorrentes
    ) {}

    /** Mesmo tamanho de período, imediatamente antes (ou o mesmo trecho do mês anterior). */
    public record Comparacao(
            LocalDate inicio,
            LocalDate fim,
            BigDecimal faturamento,
            long pedidosConcluidos,
            BigDecimal ticketMedio,
            BigDecimal variacaoFaturamento,
            BigDecimal variacaoPedidos,
            BigDecimal variacaoTicketMedio
    ) {}

    /** Um ponto da série (um dia, uma semana ou um mês), recortado para dentro do período. */
    public record PontoSerie(LocalDate inicio, LocalDate fim, BigDecimal faturamento, long pedidosConcluidos,
                             BigDecimal ticketMedio, long cancelados) {}

    /** Participação de uma modalidade/forma de pagamento nas vendas. */
    public record Fatia(String chave, long pedidos, BigDecimal faturamento, BigDecimal percentualFaturamento) {}

    /** Hora do dia (0–23) ou dia da semana (1 = segunda ... 7 = domingo). */
    public record Faixa(int chave, long pedidos, BigDecimal faturamento) {}

    public record Produto(Long produtoId, String nome, long quantidade, BigDecimal faturamento,
                          BigDecimal percentualFaturamento) {}
}
