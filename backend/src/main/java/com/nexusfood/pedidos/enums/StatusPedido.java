package com.nexusfood.pedidos.enums;

/**
 * Ciclo de vida do pedido. A ordem de declaração é a ordem do fluxo: um pedido só anda para
 * frente (pode pular etapas, ex.: CONFIRMADO → CONCLUIDO no balcão) ou é CANCELADO.
 * CONCLUIDO e CANCELADO são finais.
 */
public enum StatusPedido {
    RECEBIDO,
    CONFIRMADO,
    EM_PREPARO,
    PRONTO,
    SAIU_PARA_ENTREGA,
    CONCLUIDO,
    CANCELADO;

    public boolean isFinal() {
        return this == CONCLUIDO || this == CANCELADO;
    }

    public boolean podeIrPara(StatusPedido novo, ModalidadePedido modalidade) {
        if (isFinal() || novo == this || novo == RECEBIDO) {
            return false;
        }
        if (novo == CANCELADO) {
            return true;
        }
        if (novo == SAIU_PARA_ENTREGA && modalidade != ModalidadePedido.ENTREGA) {
            return false;
        }
        return novo.ordinal() > this.ordinal();
    }
}
