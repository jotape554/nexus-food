package com.nexusfood.pedidos.dto;

import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * O que o cliente vê ao acompanhar o pedido pelo código público. Sem telefone, sem endereço,
 * sem nada que identifique o cliente — quem tem o link só vê o andamento do pedido.
 */
public record AcompanhamentoPedidoResponse(
        String codigoPublico,
        Integer numeroDia,
        StatusPedido status,
        ModalidadePedido modalidade,
        FormaPagamento formaPagamento,
        BigDecimal subtotal,
        BigDecimal taxaEntrega,
        BigDecimal total,
        Instant criadoEm,
        Instant prontoPrevistoPara,
        Instant confirmadoEm,
        Instant emPreparoEm,
        Instant prontoEm,
        Instant saiuParaEntregaEm,
        Instant concluidoEm,
        Instant canceladoEm,
        MotivoCancelamento motivoCancelamento,
        String restauranteNome,
        String restauranteSlug,
        String restauranteTelefone,
        List<Item> itens
) {
    public record Item(String nomeProduto, Integer quantidade, BigDecimal subtotal, String observacao,
                       List<PedidoResponse.OpcaoEscolhida> opcoes) {}

    public static AcompanhamentoPedidoResponse de(Pedido p) {
        return new AcompanhamentoPedidoResponse(p.getCodigoPublico(), p.getNumeroDia(), p.getStatus(), p.getModalidade(),
                p.getFormaPagamento(), p.getSubtotal(), p.getTaxaEntrega(), p.getTotal(), p.getCriadoEm(),
                p.getProntoPrevistoPara(), p.getConfirmadoEm(), p.getEmPreparoEm(), p.getProntoEm(),
                p.getSaiuParaEntregaEm(), p.getConcluidoEm(), p.getCanceladoEm(), p.getMotivoCancelamento(),
                p.getRestaurante().getNome(), p.getRestaurante().getSlug(), p.getRestaurante().getTelefone(),
                p.getItens().stream()
                        .map(i -> new Item(i.getNomeProduto(), i.getQuantidade(), i.getSubtotal(), i.getObservacao(),
                                i.getOpcoes().stream().map(PedidoResponse.OpcaoEscolhida::de).toList()))
                        .toList());
    }
}
