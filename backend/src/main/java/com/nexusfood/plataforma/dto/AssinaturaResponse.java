package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Situação da assinatura e o que o restaurante pode usar agora.
 *
 * plano: o contratado (ou o escolhido para depois do teste). planoEfetivo: o que vale hoje
 * (PREMIUM no teste grátis, null sem assinatura válida). recursos: Recurso → liberado.
 */
public record AssinaturaResponse(
        PlanoSaas plano,
        BigDecimal precoMensal,
        String descricaoPlano,
        StatusAssinaturaSaas status,
        LocalDate dataFimTrial,
        Long diasRestantesTrial,
        boolean acessoLiberado,
        PlanoSaas planoEfetivo,
        Map<String, Boolean> recursos,
        Integer limiteUsuarios,
        long usuariosAtivos,
        LocalDate primeiroDiaRelatorio,
        boolean usuarioForaDoLimite,
        boolean assinaturaNaStripe
) {}
