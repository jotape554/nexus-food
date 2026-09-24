package com.nexusfood.analytics.regras;

import java.util.List;

/**
 * Uma versão publicada da regra do Nexus Score (arquivo analytics/regras/nexus-score-vN.json).
 * Versão publicada nunca muda: nova regra = nova versão, e cada nota guardada diz com qual
 * versão foi calculada.
 */
public record RegraScore(
        String versao,
        String publicadaEm,
        String observacao,
        int janelaDias,
        Historico historico,
        List<Faixa> faixas,
        List<Area> areas
) {
    public record Historico(int diasMinimosNota, int diasNotaOficial, long pedidosConcluidosMinimos,
                            double pesoValidoMinimo, double pesoValidoMinimoArea) {}

    public record Faixa(String codigo, String nome, int de, int ate) {}

    public record Area(String codigo, String nome, double peso, List<Indicador> indicadores) {}

    public record Indicador(String codigo, String nome, String descricao, double peso, String unidade,
                            Ancoras ancoras, Minimos minimos) {}

    /** Valor que vale 0, 60 e 100 pontos. Se "zero" for maior que "cem", menor é melhor. */
    public record Ancoras(double zero, double referencia, double cem) {}

    /** Condições para o indicador valer; qualquer uma que falte deixa o indicador "sem dados". */
    public record Minimos(Integer diasHistorico, Long amostra, Double cobertura) {}

    public Faixa faixaDaNota(int nota) {
        return faixas.stream().filter(f -> nota >= f.de() && nota <= f.ate()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Nota fora das faixas: " + nota));
    }
}
