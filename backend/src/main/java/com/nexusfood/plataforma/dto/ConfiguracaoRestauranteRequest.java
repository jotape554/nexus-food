package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.TipoTaxaEntrega;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Tudo que o próprio restaurante configura no painel: dados, modalidades, taxa e horários. */
@Data
public class ConfiguracaoRestauranteRequest {

    @NotBlank(message = "Informe o nome do restaurante.")
    @Size(max = 255, message = "Nome muito longo.")
    private String nome;

    private String telefone;
    private String endereco;
    private String logoUrl;

    @NotBlank(message = "Informe o fuso horário.")
    private String fusoHorario;

    @NotNull(message = "Informe o horário de virada do dia.")
    private LocalTime horaViradaDia;

    private boolean aceitaRetirada;
    private boolean aceitaEntrega;
    private boolean aceitaConsumoLocal;

    @NotNull(message = "Informe como a taxa de entrega é cobrada.")
    private TipoTaxaEntrega tipoTaxaEntrega;

    @NotNull(message = "Informe a taxa de entrega.")
    @DecimalMin(value = "0.00", message = "A taxa de entrega não pode ser negativa.")
    private BigDecimal taxaEntregaFixa;

    @NotNull(message = "Informe o pedido mínimo.")
    @DecimalMin(value = "0.00", message = "O pedido mínimo não pode ser negativo.")
    private BigDecimal pedidoMinimo;

    @NotNull(message = "Informe o tempo estimado de preparo.")
    @Min(value = 1, message = "O tempo de preparo deve ser de pelo menos 1 minuto.")
    @Max(value = 300, message = "O tempo de preparo deve ser de no máximo 300 minutos.")
    private Integer tempoPreparoEstimadoMin;
}
