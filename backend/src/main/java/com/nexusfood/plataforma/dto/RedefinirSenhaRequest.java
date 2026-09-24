package com.nexusfood.plataforma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RedefinirSenhaRequest {

    @NotBlank(message = "Link inválido.")
    private String token;

    @NotBlank(message = "Informe a nova senha.")
    @Size(min = 6, message = "Use pelo menos 6 caracteres na senha.")
    private String novaSenha;
}
