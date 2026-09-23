package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AssinaturaResponse {
    private PlanoSaas plano;
    private BigDecimal precoMensal;
    private String descricaoPlano;
    private StatusAssinaturaSaas status;
    private LocalDate dataFimTrial;
    private Long diasRestantesTrial;
    private boolean acessoLiberado;
}
