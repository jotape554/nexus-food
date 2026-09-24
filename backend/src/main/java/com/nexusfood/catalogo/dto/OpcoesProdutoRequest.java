package com.nexusfood.catalogo.dto;

import com.nexusfood.catalogo.enums.CobrancaGrupo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Todos os grupos de opções do produto de uma vez, na ordem da tela. Grupo ou opção com id é
 * atualizado; sem id é criado; o que não vier é removido (pedidos antigos guardam cópia).
 */
public record OpcoesProdutoRequest(
        @NotNull(message = "Envie a lista de grupos (pode ser vazia).")
        @Size(max = 10, message = "Use no máximo 10 grupos de opções por produto.")
        @Valid
        List<Grupo> grupos
) {
    public record Grupo(
            Long id,
            @NotBlank(message = "Dê um nome ao grupo (ex.: Tamanho, Adicionais).")
            @Size(max = 80, message = "Nome do grupo muito longo.")
            String nome,
            @NotNull(message = "Informe o mínimo de escolhas do grupo.")
            @Min(value = 0, message = "O mínimo de escolhas não pode ser negativo.")
            Integer minimo,
            @NotNull(message = "Informe o máximo de escolhas do grupo.")
            @Min(value = 1, message = "O máximo de escolhas deve ser pelo menos 1.")
            @Max(value = 30, message = "O máximo de escolhas é 30.")
            Integer maximo,
            CobrancaGrupo cobranca,
            @NotEmpty(message = "Cada grupo precisa de pelo menos uma opção.")
            @Size(max = 30, message = "Use no máximo 30 opções por grupo.")
            @Valid
            List<Opcao> opcoes
    ) {}

    public record Opcao(
            Long id,
            @NotBlank(message = "Dê um nome a cada opção.")
            @Size(max = 80, message = "Nome da opção muito longo.")
            String nome,
            @NotNull(message = "Informe o preço da opção (0 se não cobra nada).")
            @DecimalMin(value = "0.00", message = "O preço da opção não pode ser negativo.")
            BigDecimal preco,
            Boolean disponivel
    ) {}
}
