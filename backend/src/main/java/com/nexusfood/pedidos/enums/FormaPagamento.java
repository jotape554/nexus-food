package com.nexusfood.pedidos.enums;

/** Só declarada pelo cliente no pedido; o pagamento acontece na entrega/balcão (sem pagamento online). */
public enum FormaPagamento {
    DINHEIRO,
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO
}
