package com.nexusfood.pedidos.enums;

import lombok.Getter;

/**
 * Motivo do cancelamento e de quem foi a iniciativa. O indicador de "cancelamento pelo
 * restaurante" do Nexus Score conta só os motivos cuja responsabilidade é do restaurante.
 */
@Getter
public enum MotivoCancelamento {
    RECUSADO_PELO_RESTAURANTE(CanceladoPor.RESTAURANTE),
    ITEM_INDISPONIVEL(CanceladoPor.RESTAURANTE),
    FORA_DA_AREA_DE_ENTREGA(CanceladoPor.RESTAURANTE),
    CLIENTE_DESISTIU(CanceladoPor.CLIENTE),
    NAO_ENTREGUE(CanceladoPor.RESTAURANTE),
    OUTRO(CanceladoPor.RESTAURANTE);

    private final CanceladoPor responsavel;

    MotivoCancelamento(CanceladoPor responsavel) {
        this.responsavel = responsavel;
    }
}
