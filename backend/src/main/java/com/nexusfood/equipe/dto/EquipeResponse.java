package com.nexusfood.equipe.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;

import java.util.List;

/**
 * A equipe e o limite do plano. limiteUsuarios null = sem limite. proximoPlano: o plano que
 * permitiria mais um usuário quando o limite está cheio (null se ainda cabe ou se não há).
 */
public record EquipeResponse(
        List<UsuarioEquipeResponse> usuarios,
        long usuariosAtivos,
        Integer limiteUsuarios,
        PlanoSaas planoEfetivo,
        PlanoSaas proximoPlano
) {}
