package com.nexusfood.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoriaRequest {

    @NotBlank(message = "Informe o nome da categoria.")
    @Size(max = 120, message = "Nome da categoria muito longo.")
    private String nome;

    private Integer ordem;

    private boolean ativa = true;
}
