package com.nexusfood.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EsqueciSenhaRequest {

    @NotBlank(message = "Informe um e-mail.")
    @Email(message = "Informe um e-mail válido.")
    private String email;
}
