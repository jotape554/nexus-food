package com.nexusfood.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tela do Nexus: nota, áreas, cada indicador com valor/pontos/motivo, evolução e insights.
 * Valores de indicador na unidade da regra: PERCENTUAL (12.5 = 12,5%), MINUTOS, COEFICIENTE.
 */
public record NexusResponse(
        LocalDate dia,
        LocalDate inicioJanela,
        String regraVersao,
        String situacao,
        Integer nota,
        String faixa,
        String faixaNome,
        String motivoSemNota,
        BigDecimal pesoValido,
        int diasHistorico,
        int diasParaNota,
        int diasParaOficial,
        int pedidosConcluidosJanela,
        long pedidosConcluidosMinimos,
        Integer variacao7Dias,
        List<Faixa> faixas,
        List<Area> areas,
        List<PontoHistorico> historico,
        List<Insight> insights
) {
    public record Faixa(String codigo, String nome, int de, int ate) {}

    public record Area(String codigo, String nome, BigDecimal peso, BigDecimal nota, boolean exibida, List<Indicador> indicadores) {}

    /** pesoNaArea: peso do indicador dentro da área pela regra; pesoEfetivo: quanto ele pesou de fato na nota. */
    public record Indicador(String codigo, String nome, String descricao, String unidade, BigDecimal valor, BigDecimal pontos,
                            BigDecimal pesoNaArea, BigDecimal pesoEfetivo, String status, String motivo, long amostra, Ancoras ancoras) {}

    public record Ancoras(double zero, double referencia, double cem) {}

    public record PontoHistorico(LocalDate dia, Integer nota, String situacao) {}

    public record Insight(Long id, LocalDate dia, String severidade, String texto) {}
}
