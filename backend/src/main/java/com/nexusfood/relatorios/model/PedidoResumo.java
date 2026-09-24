package com.nexusfood.relatorios.model;

import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Só as colunas do pedido que as métricas usam — carregado direto por consulta, sem montar a
 * entidade (nem itens, nem eventos). É a entrada do MetricasCalculator (relatórios e Nexus).
 */
public record PedidoResumo(
        Long clienteId,
        LocalDate diaOperacional,
        Instant criadoEm,
        StatusPedido status,
        CanceladoPor canceladoPor,
        ModalidadePedido modalidade,
        FormaPagamento formaPagamento,
        BigDecimal total,
        MotivoCancelamento motivoCancelamento,
        Instant confirmadoEm,
        Instant prontoEm,
        Instant prontoPrevistoPara
) {
    public boolean concluido() {
        return status == StatusPedido.CONCLUIDO;
    }

    public boolean cancelado() {
        return status == StatusPedido.CANCELADO;
    }
}
