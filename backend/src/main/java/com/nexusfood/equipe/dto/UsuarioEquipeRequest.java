package com.nexusfood.equipe.dto;

import com.nexusfood.plataforma.enums.Papel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Convite de um novo usuário. O e-mail só é lido na criação; depois é a identidade da pessoa. */
public record UsuarioEquipeRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome muito longo.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") String email,
        @NotNull(message = "Escolha o perfil.") Papel papel
) {}
