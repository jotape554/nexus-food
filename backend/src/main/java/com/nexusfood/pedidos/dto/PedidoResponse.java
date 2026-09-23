package com.nexusfood.pedidos.dto;

import com.nexusfood.pedidos.enums.*;
import com.nexusfood.pedidos.model.ItemPedido;
import com.nexusfood.pedidos.model.Pedido;
import com.nexusfood.pedidos.model.PedidoEvento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Pedido como o painel do restaurante enxerga (com dados do cliente e histórico de status). */
public record PedidoResponse(
        Long id,
        Integer numeroDia,
        String codigoPublico,
        StatusPedido status,
        ModalidadePedido modalidade,
        FormaPagamento formaPagamento,
        BigDecimal trocoPara,
        String enderecoEntrega,
        String bairroEntrega,
        String observacao,
        BigDecimal subtotal,
        BigDecimal taxaEntrega,
        BigDecimal total,
        LocalDate diaOperacional,
        Instant prontoPrevistoPara,
        Instant criadoEm,
        Instant confirmadoEm,
        Instant emPreparoEm,
        Instant prontoEm,
        Instant saiuParaEntregaEm,
        Instant concluidoEm,
        Instant canceladoEm,
        MotivoCancelamento motivoCancelamento,
        CanceladoPor canceladoPor,
        ClienteResumo cliente,
        List<Item> itens,
        List<Evento> eventos
) {
    public record ClienteResumo(Long id, String nome, String telefone) {}

    public record Item(Long produtoId, String nomeProduto, BigDecimal precoUnitario, Integer quantidade,
                       BigDecimal subtotal, String observacao) {
        static Item de(ItemPedido i) {
            return new Item(i.getProduto().getId(), i.getNomeProduto(), i.getPrecoUnitario(), i.getQuantidade(),
                    i.getSubtotal(), i.getObservacao());
        }
    }

    public record Evento(StatusPedido statusAnterior, StatusPedido statusNovo, Instant ocorridoEm, String usuario) {
        static Evento de(PedidoEvento e) {
            return new Evento(e.getStatusAnterior(), e.getStatusNovo(), e.getOcorridoEm(),
                    e.getUsuario() != null ? e.getUsuario().getNome() : null);
        }
    }

    public static PedidoResponse de(Pedido p, boolean comEventos) {
        return new PedidoResponse(p.getId(), p.getNumeroDia(), p.getCodigoPublico(), p.getStatus(), p.getModalidade(),
                p.getFormaPagamento(), p.getTrocoPara(), p.getEnderecoEntrega(), p.getBairroEntrega(), p.getObservacao(),
                p.getSubtotal(), p.getTaxaEntrega(), p.getTotal(), p.getDiaOperacional(), p.getProntoPrevistoPara(),
                p.getCriadoEm(), p.getConfirmadoEm(), p.getEmPreparoEm(), p.getProntoEm(), p.getSaiuParaEntregaEm(),
                p.getConcluidoEm(), p.getCanceladoEm(), p.getMotivoCancelamento(), p.getCanceladoPor(),
                new ClienteResumo(p.getCliente().getId(), p.getCliente().getNome(), p.getCliente().getTelefone()),
                p.getItens().stream().map(Item::de).toList(),
                comEventos ? p.getEventos().stream().map(Evento::de).toList() : List.of());
    }
}
