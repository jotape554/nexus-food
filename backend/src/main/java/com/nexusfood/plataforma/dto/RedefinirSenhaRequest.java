package com.nexusfood.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedefinirSenhaRequest {

    @NotBlank(message = "Link inválido.")
    private String token;

    @NotBlank(message = "Informe a nova senha.")
    private String novaSenha;
}
