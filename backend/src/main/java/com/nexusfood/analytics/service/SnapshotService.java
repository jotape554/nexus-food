package com.nexusfood.analytics.service;

import com.nexusfood.analytics.model.SnapshotDiario;
import com.nexusfood.analytics.model.SnapshotProdutoDiario;
import com.nexusfood.analytics.repository.SnapshotDiarioRepository;
import com.nexusfood.analytics.repository.SnapshotProdutoDiarioRepository;
import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.relatorios.model.PedidoResumo;
import com.nexusfood.relatorios.model.ProdutoVendido;
import com.nexusfood.relatorios.repository.RelatorioRepository;
import com.nexusfood.relatorios.service.MetricasCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera (ou regera) o resumo de um dia operacional. Idempotente: apaga o resumo do dia e grava
 * de novo, então rodar duas vezes nunca duplica. Usa o MetricasCalculator dos relatórios, então
 * o resumo do dia bate com o relatório daquele dia.
 */
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final RelatorioRepository relatorioRepository;
    private final SnapshotDiarioRepository snapshotRepository;
    private final SnapshotProdutoDiarioRepository snapshotProdutoRepository;
    private final MetricasCalculator calculadora;
    private final Clock clock;

    public void gerarDia(Long restauranteId, LocalDate dia, ZoneId fuso) {
        List<PedidoResumo> pedidos = relatorioRepository.pedidosDoPeriodo(restauranteId, dia, dia);
        List<ProdutoVendido> produtos = relatorioRepository.produtosVendidos(restauranteId, dia, dia, StatusPedido.CONCLUIDO);
        long clientesNovos = relatorioRepository.clientesNovos(restauranteId, dia, dia, StatusPedido.CONCLUIDO);
        MetricasCalculator.Totais t = calculadora.totais(pedidos);

        List<PedidoResumo> concluidos = pedidos.stream().filter(PedidoResumo::concluido).toList();
        List<PedidoResumo> comPronto = concluidos.stream().filter(p -> p.prontoEm() != null).toList();
        long noPrazo = comPronto.stream().filter(p -> !p.prontoEm().isAfter(p.prontoPrevistoPara())).count();
        String porHora = calculadora.porHora(pedidos, fuso).stream()
                .map(f -> String.valueOf(f.pedidos())).collect(Collectors.joining(","));

        snapshotRepository.apagarDia(restauranteId, dia);
        snapshotProdutoRepository.apagarDia(restauranteId, dia);

        Instant agora = Instant.now(clock);
        snapshotRepository.save(SnapshotDiario.builder()
                .restauranteId(restauranteId)
                .dia(dia)
                .pedidosRecebidos((int) t.pedidosRecebidos())
                .pedidosConcluidos((int) t.pedidosConcluidos())
                .canceladosRestaurante((int) t.canceladosPeloRestaurante())
                .canceladosCliente((int) pedidos.stream().filter(p -> p.cancelado() && p.canceladoPor() == CanceladoPor.CLIENTE).count())
                .faturamento(t.faturamento())
                .itensVendidos((int) produtos.stream().mapToLong(ProdutoVendido::quantidade).sum())
                .clientesUnicos((int) t.clientesUnicos())
                .clientesNovos((int) clientesNovos)
                .pedidosComPronto(comPronto.size())
                .prontosNoPrazo((int) noPrazo)
                .pedidosPorHora(porHora)
                .versaoCalculo(SnapshotDiario.VERSAO_CALCULO)
                .calculadoEm(agora)
                .build());

        produtos.forEach(p -> snapshotProdutoRepository.save(SnapshotProdutoDiario.builder()
                .restauranteId(restauranteId)
                .dia(dia)
                .produtoId(p.produtoId())
                .quantidade(p.quantidade().intValue())
                .faturamento(p.faturamento())
                .build()));
    }
}
