package com.nexusfood.pedidos.dto;

import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MudarStatusRequest {

    @NotNull(message = "Informe o novo status.")
    private StatusPedido status;

    /** Obrigatório quando status = CANCELADO. */
    private MotivoCancelamento motivoCancelamento;
}
