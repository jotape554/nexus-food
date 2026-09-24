package com.nexusfood.equipe.dto;

import com.nexusfood.plataforma.enums.Papel;

/**
 * voce: é quem está logado. foraDoLimite: ativo, mas além do limite do plano atual (sem acesso
 * até a equipe ou o plano serem ajustados).
 */
public record UsuarioEquipeResponse(
        Long id,
        String nome,
        String email,
        Papel papel,
        boolean ativo,
        boolean convitePendente,
        boolean voce,
        boolean foraDoLimite
) {}
