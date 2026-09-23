package com.nexusfood.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProdutoRequest {

    @NotNull(message = "Escolha a categoria do produto.")
    private Long categoriaId;

    @NotBlank(message = "Informe o nome do produto.")
    @Size(max = 160, message = "Nome do produto muito longo.")
    private String nome;

    @Size(max = 1000, message = "Descrição muito longa.")
    private String descricao;

    @NotNull(message = "Informe o preço do produto.")
    @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero.")
    private BigDecimal preco;

    @Size(max = 500, message = "Endereço da imagem muito longo.")
    private String imagemUrl;

    private boolean disponivel = true;

    private Integer ordem;
}
