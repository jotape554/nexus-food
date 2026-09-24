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
        List<Insight> insights,
        /** false no plano Profissional: nota e áreas sim; valor de cada indicador e dicas, não. */
        boolean detalhesLiberados,
        /** Quantas dicas existem e o plano não mostra (para a tela dizer "3 dicas no Premium"). */
        int insightsBloqueados
) {
    /**
     * Versão para quem tem a nota mas não os detalhes: mantém o que cada indicador mede e a
     * regra (âncoras são públicas), tira o resultado de cada um e o texto das dicas.
     */
    public NexusResponse semDetalhes() {
        List<Area> areasSemDetalhe = areas.stream().map(a -> new Area(a.codigo(), a.nome(), a.peso(), a.nota(), a.exibida(),
                a.indicadores().stream().map(i -> new Indicador(i.codigo(), i.nome(), i.descricao(), i.unidade(),
                        null, null, i.pesoNaArea(), null, "BLOQUEADO", null, 0, i.ancoras())).toList())).toList();
        return new NexusResponse(dia, inicioJanela, regraVersao, situacao, nota, faixa, faixaNome, motivoSemNota, pesoValido,
                diasHistorico, diasParaNota, diasParaOficial, pedidosConcluidosJanela, pedidosConcluidosMinimos, variacao7Dias,
                faixas, areasSemDetalhe, historico, List.of(), false, insights.size());
    }

    public record Faixa(String codigo, String nome, int de, int ate) {}

    public record Area(String codigo, String nome, BigDecimal peso, BigDecimal nota, boolean exibida, List<Indicador> indicadores) {}

    /** pesoNaArea: peso do indicador dentro da área pela regra; pesoEfetivo: quanto ele pesou de fato na nota. */
    public record Indicador(String codigo, String nome, String descricao, String unidade, BigDecimal valor, BigDecimal pontos,
                            BigDecimal pesoNaArea, BigDecimal pesoEfetivo, String status, String motivo, long amostra, Ancoras ancoras) {}

    public record Ancoras(double zero, double referencia, double cem) {}

    public record PontoHistorico(LocalDate dia, Integer nota, String situacao) {}

    public record Insight(Long id, LocalDate dia, String severidade, String texto) {}
}
