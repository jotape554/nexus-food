package com.nexusfood.plataforma.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BairroEntregaRequest {

    @NotBlank(message = "Informe o nome do bairro.")
    @Size(max = 120, message = "Nome do bairro muito longo.")
    private String nome;

    @NotNull(message = "Informe a taxa de entrega do bairro.")
    @DecimalMin(value = "0.00", message = "A taxa de entrega não pode ser negativa.")
    private BigDecimal taxa;

    private boolean ativo = true;
}
