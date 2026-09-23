package com.nexusfood.pedidos.dto;

import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pedido enviado pelo cardápio público. Repare que não há preço nem total aqui: o servidor
 * sempre recalcula a partir do cadastro do produto — nunca confia no valor vindo do navegador.
 */
@Data
public class CriarPedidoRequest {

    @NotBlank(message = "Pedido sem identificador. Recarregue a página e tente de novo.")
    @Size(max = 64, message = "Identificador do pedido inválido.")
    private String chaveIdempotencia;

    @NotBlank(message = "Informe seu nome.")
    @Size(max = 160, message = "Nome muito longo.")
    private String nomeCliente;

    @NotBlank(message = "Informe seu telefone.")
    private String telefoneCliente;

    @NotNull(message = "Escolha retirada, entrega ou consumo no local.")
    private ModalidadePedido modalidade;

    @NotNull(message = "Escolha a forma de pagamento.")
    private FormaPagamento formaPagamento;

    private BigDecimal trocoPara;

    @Size(max = 500, message = "Endereço muito longo.")
    private String enderecoEntrega;

    /** Obrigatório para entrega quando o restaurante cobra taxa por bairro. */
    private Long bairroId;

    @Size(max = 500, message = "Observação muito longa.")
    private String observacao;

    @NotEmpty(message = "Adicione pelo menos um item ao pedido.")
    @Size(max = 50, message = "Pedido com itens demais.")
    @Valid
    private List<Item> itens;

    @Data
    public static class Item {

        @NotNull(message = "Item sem produto.")
        private Long produtoId;

        @NotNull(message = "Informe a quantidade.")
        @Min(value = 1, message = "A quantidade mínima é 1.")
        @Max(value = 99, message = "A quantidade máxima por item é 99.")
        private Integer quantidade;

        @Size(max = 300, message = "Observação do item muito longa.")
        private String observacao;
    }
}
