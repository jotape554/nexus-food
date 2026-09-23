package com.nexusfood.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Cadastro de um NOVO restaurante (conta) + seu usuário administrador. */
@Data
public class RegistroRequest {

    @NotBlank(message = "Informe o nome do restaurante.")
    private String nomeRestaurante;

    @NotBlank(message = "Informe seu nome.")
    private String nomeAdmin;

    @NotBlank(message = "Informe um e-mail.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    @NotBlank(message = "Informe seu CPF.")
    private String cpf;

    @NotBlank(message = "Informe uma senha.")
    private String senha;
}
