package com.nexusfood.plataforma.dto;

import com.nexusfood.plataforma.enums.PlanoSaas;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PlanoDisponivelResponse {
    private PlanoSaas plano;
    private BigDecimal precoMensal;
    private String descricao;
}
