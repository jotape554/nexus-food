package com.nexusfood.pedidos;

import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.Pedido;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nexusfood.pedidos.enums.StatusPedido.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTransicaoTest {

    private static final Instant T0 = Instant.parse("2026-09-23T18:00:00Z");

    private Pedido pedido(ModalidadePedido modalidade) {
        Pedido p = Pedido.builder().modalidade(modalidade).criadoEm(T0).build();
        p.registrarRecebimento();
        return p;
    }

    @Test
    void fluxoCompletoDeEntregaGravaHorarioDeCadaEtapaEUmEventoPorMudanca() {
        Pedido p = pedido(ModalidadePedido.ENTREGA);
        p.transicionarPara(CONFIRMADO, T0.plusSeconds(60), null, null);
        p.transicionarPara(EM_PREPARO, T0.plusSeconds(120), null, null);
        p.transicionarPara(PRONTO, T0.plusSeconds(1200), null, null);
        p.transicionarPara(SAIU_PARA_ENTREGA, T0.plusSeconds(1300), null, null);
        p.transicionarPara(CONCLUIDO, T0.plusSeconds(2000), null, null);

        assertThat(p.getStatus()).isEqualTo(CONCLUIDO);
        assertThat(p.getConfirmadoEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(p.getEmPreparoEm()).isEqualTo(T0.plusSeconds(120));
        assertThat(p.getProntoEm()).isEqualTo(T0.plusSeconds(1200));
        assertThat(p.getSaiuParaEntregaEm()).isEqualTo(T0.plusSeconds(1300));
        assertThat(p.getConcluidoEm()).isEqualTo(T0.plusSeconds(2000));
        assertThat(p.getEventos()).extracting("statusNovo")
                .containsExactly(RECEBIDO, CONFIRMADO, EM_PREPARO, PRONTO, SAIU_PARA_ENTREGA, CONCLUIDO);
        assertThat(p.getEventos()).extracting("statusAnterior")
                .containsExactly(null, RECEBIDO, CONFIRMADO, EM_PREPARO, PRONTO, SAIU_PARA_ENTREGA);
    }

    @Test
    void pularDoRecebidoDiretoParaPreparoContaComoAceite() {
        Pedido p = pedido(ModalidadePedido.RETIRADA);
        Instant t = T0.plusSeconds(90);
        p.transicionarPara(EM_PREPARO, t, null, null);

        assertThat(p.getConfirmadoEm()).isEqualTo(t);
        assertThat(p.getEmPreparoEm()).isEqualTo(t);
    }

    @Test
    void etapasPuladasDepoisDoAceiteFicamSemHorario() {
        Pedido p = pedido(ModalidadePedido.CONSUMO_LOCAL);
        p.transicionarPara(CONFIRMADO, T0.plusSeconds(30), null, null);
        p.transicionarPara(CONCLUIDO, T0.plusSeconds(900), null, null);

        assertThat(p.getProntoEm()).isNull();
        assertThat(p.getEmPreparoEm()).isNull();
    }

    @Test
    void naoVoltaParaTrasNemSaiDeStatusFinal() {
        Pedido p = pedido(ModalidadePedido.RETIRADA);
        p.transicionarPara(PRONTO, T0.plusSeconds(600), null, null);

        assertThatThrownBy(() -> p.transicionarPara(EM_PREPARO, T0.plusSeconds(700), null, null))
                .isInstanceOf(RegraDeNegocioException.class);

        p.transicionarPara(CONCLUIDO, T0.plusSeconds(800), null, null);
        assertThatThrownBy(() -> p.transicionarPara(CANCELADO, T0.plusSeconds(900), null, MotivoCancelamento.OUTRO))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void saiuParaEntregaSoExisteEmPedidoDeEntrega() {
        assertThat(PRONTO.podeIrPara(SAIU_PARA_ENTREGA, ModalidadePedido.RETIRADA)).isFalse();
        assertThat(PRONTO.podeIrPara(SAIU_PARA_ENTREGA, ModalidadePedido.ENTREGA)).isTrue();
    }

    @Test
    void cancelamentoExigeMotivoEDefineResponsavel() {
        Pedido p = pedido(ModalidadePedido.ENTREGA);
        assertThatThrownBy(() -> p.transicionarPara(CANCELADO, T0.plusSeconds(10), null, null))
                .isInstanceOf(RegraDeNegocioException.class);

        p.transicionarPara(CANCELADO, T0.plusSeconds(20), null, MotivoCancelamento.CLIENTE_DESISTIU);
        assertThat(p.getCanceladoPor()).isEqualTo(CanceladoPor.CLIENTE);
        assertThat(p.getCanceladoEm()).isEqualTo(T0.plusSeconds(20));
        assertThat(p.getConfirmadoEm()).as("cancelar não conta como aceitar").isNull();
    }

    @Test
    void recusaPeloRestauranteFicaNaContaDoRestaurante() {
        Pedido p = pedido(ModalidadePedido.RETIRADA);
        p.transicionarPara(CANCELADO, T0.plusSeconds(20), null, MotivoCancelamento.RECUSADO_PELO_RESTAURANTE);
        assertThat(p.getCanceladoPor()).isEqualTo(CanceladoPor.RESTAURANTE);
        assertThat(p.getStatus()).isEqualTo(StatusPedido.CANCELADO);
    }
}
