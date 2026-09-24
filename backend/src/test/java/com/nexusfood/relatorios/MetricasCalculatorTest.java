package com.nexusfood.relatorios;

import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.relatorios.model.PedidoResumo;
import com.nexusfood.relatorios.service.MetricasCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricasCalculatorTest {

    private final MetricasCalculator calc = new MetricasCalculator();
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 21);

    private static PedidoResumo pedido(long cliente, StatusPedido status, CanceladoPor por, ModalidadePedido mod,
                                       String total, String criadoEmUtc, LocalDate dia) {
        return new PedidoResumo(cliente, dia, Instant.parse(criadoEmUtc), status, por, mod, FormaPagamento.PIX, new BigDecimal(total));
    }

    private final List<PedidoResumo> pedidos = List.of(
            pedido(1, StatusPedido.CONCLUIDO, null, ModalidadePedido.RETIRADA, "50.00", "2026-09-21T15:00:00Z", SEGUNDA),
            pedido(1, StatusPedido.CONCLUIDO, null, ModalidadePedido.ENTREGA, "30.00", "2026-09-21T23:10:00Z", SEGUNDA),
            pedido(2, StatusPedido.CONCLUIDO, null, ModalidadePedido.ENTREGA, "20.01", "2026-09-22T04:30:00Z", SEGUNDA),
            pedido(3, StatusPedido.CANCELADO, CanceladoPor.RESTAURANTE, ModalidadePedido.RETIRADA, "40.00", "2026-09-21T16:00:00Z", SEGUNDA),
            pedido(4, StatusPedido.CANCELADO, CanceladoPor.CLIENTE, ModalidadePedido.RETIRADA, "10.00", "2026-09-21T16:05:00Z", SEGUNDA),
            pedido(5, StatusPedido.EM_PREPARO, null, ModalidadePedido.RETIRADA, "15.00", "2026-09-21T17:00:00Z", SEGUNDA));

    @Test
    void totaisContamSoConcluidosComoVenda() {
        MetricasCalculator.Totais t = calc.totais(pedidos);
        assertThat(t.faturamento()).isEqualByComparingTo("100.01");
        assertThat(t.pedidosConcluidos()).isEqualTo(3);
        assertThat(t.pedidosRecebidos()).isEqualTo(6);
        assertThat(t.ticketMedio()).isEqualByComparingTo("33.34"); // 100,01 / 3, arredondado
        assertThat(t.cancelados()).isEqualTo(2);
        assertThat(t.canceladosPeloRestaurante()).isEqualTo(1);
        assertThat(t.taxaCancelamento()).isEqualByComparingTo("33.3");
        assertThat(t.clientesUnicos()).isEqualTo(2);
    }

    @Test
    void semPedidosTudoZeroSemDividirPorZero() {
        MetricasCalculator.Totais t = calc.totais(List.of());
        assertThat(t.faturamento()).isEqualByComparingTo("0");
        assertThat(t.ticketMedio()).isEqualByComparingTo("0");
        assertThat(t.taxaCancelamento()).isEqualByComparingTo("0");
    }

    @Test
    void horaUsaOFusoDoRestauranteESempreTem24Faixas() {
        var porHora = calc.porHora(pedidos, ZoneId.of("America/Sao_Paulo"));
        assertThat(porHora).hasSize(24);
        assertThat(porHora.get(12).faturamento()).isEqualByComparingTo("50.00"); // 15:00Z = 12:00 em SP
        assertThat(porHora.get(20).faturamento()).isEqualByComparingTo("30.00"); // 23:10Z = 20:10
        assertThat(porHora.get(1).pedidos()).isEqualTo(1);                       // 04:30Z = 01:30 da madrugada
        assertThat(porHora.get(13).pedidos()).as("cancelado não é venda").isZero();
    }

    @Test
    void diaDaSemanaUsaODiaOperacional() {
        var porDia = calc.porDiaDaSemana(pedidos);
        assertThat(porDia).hasSize(7);
        assertThat(porDia.get(0).pedidos()).as("a venda de 01:30 de terça conta na segunda").isEqualTo(3);
    }

    @Test
    void fatiasOrdenadasPorFaturamento() {
        var porModalidade = calc.porChave(pedidos, PedidoResumo::modalidade);
        assertThat(porModalidade).extracting(MetricasCalculator.Fatia::chave)
                .containsExactly(ModalidadePedido.ENTREGA, ModalidadePedido.RETIRADA); // 50,01 > 50,00
        assertThat(porModalidade.get(0).faturamento()).isEqualByComparingTo("50.01");
        assertThat(porModalidade.get(0).pedidos()).isEqualTo(2);
    }

    @Test
    void variacaoPercentualENulaSemBase() {
        assertThat(MetricasCalculator.variacao(new BigDecimal("150"), new BigDecimal("100"))).isEqualByComparingTo("50.0");
        assertThat(MetricasCalculator.variacao(new BigDecimal("80"), new BigDecimal("100"))).isEqualByComparingTo("-20.0");
        assertThat(MetricasCalculator.variacao(new BigDecimal("80"), BigDecimal.ZERO)).isNull();
    }
}
