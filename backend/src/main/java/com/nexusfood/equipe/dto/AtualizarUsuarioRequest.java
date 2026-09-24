package com.nexusfood.equipe.dto;

import com.nexusfood.plataforma.enums.Papel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AtualizarUsuarioRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome muito longo.") String nome,
        @NotNull(message = "Escolha o perfil.") Papel papel
) {}
