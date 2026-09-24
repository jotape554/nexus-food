package com.nexusfood.analytics.service;

import com.nexusfood.analytics.regras.RegraScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Motor do Nexus Score: recebe as medidas já calculadas e a regra, devolve a nota. Função pura
 * (sem banco, sem relógio), então cada caso é testável com uma tabela.
 *
 * Passos:
 * 1. cada indicador vira 0–100 pontos por interpolação linear entre as âncoras (0 / 60 / 100),
 *    com trava nos extremos; indicador que não atinge o mínimo fica SEM_DADOS (nunca vale zero);
 * 2. nota da área = média ponderada dos indicadores válidos (pesos redistribuídos); a área só
 *    aparece se tiver ao menos 50% do seu peso com dados;
 * 3. nota final = média ponderada das áreas que aparecem;
 * 4. situação: COLETANDO (sem nota), PROVISORIA (28–55 dias) ou OFICIAL (56+ dias). Há nota
 *    quando as áreas que aparecem somam ao menos 60% do peso total.
 */
@Component
public class NexusScoreEngine {

    public enum Situacao { COLETANDO, PROVISORIA, OFICIAL }

    public enum StatusIndicador { OK, SEM_DADOS }

    /** Valor medido de um indicador, com o tamanho da amostra e (quando faz sentido) a cobertura 0–1. */
    public record Medida(Double valor, long amostra, Double cobertura) {
        public static Medida semValor(long amostra) {
            return new Medida(null, amostra, null);
        }
    }

    public record Entrada(int diasHistorico, long pedidosConcluidosJanela, Map<String, Medida> medidas) {}

    public record ResultadoIndicador(String codigo, String area, Double valor, Double pontos, double pesoEfetivo,
                                     StatusIndicador status, String motivo, long amostra) {}

    public record ResultadoArea(String codigo, Double nota, double peso, double pesoValido, boolean exibida) {}

    public record Resultado(String regraVersao, Situacao situacao, Integer nota, String faixa, double pesoValido,
                            int diasHistorico, long pedidosConcluidosJanela, String motivoSemNota,
                            List<ResultadoArea> areas, List<ResultadoIndicador> indicadores) {}

    public Resultado calcular(RegraScore regra, Entrada entrada) {
        RegraScore.Historico h = regra.historico();

        record Parcial(RegraScore.Area area, List<RegraScore.Indicador> indicadores, List<Double> pontos, List<String> motivos,
                       List<Medida> medidas, double pesoValidoArea, Double nota) {}

        List<Parcial> parciais = new ArrayList<>();
        for (RegraScore.Area area : regra.areas()) {
            List<Double> pontos = new ArrayList<>();
            List<String> motivos = new ArrayList<>();
            List<Medida> medidas = new ArrayList<>();
            double pesoValido = 0;
            double soma = 0;
            for (RegraScore.Indicador ind : area.indicadores()) {
                Medida m = entrada.medidas().get(ind.codigo());
                medidas.add(m);
                String motivo = motivoSemDados(ind, m, entrada.diasHistorico());
                motivos.add(motivo);
                if (motivo == null) {
                    double p = pontos(ind.ancoras(), m.valor());
                    pontos.add(p);
                    pesoValido += ind.peso();
                    soma += p * ind.peso();
                } else {
                    pontos.add(null);
                }
            }
            boolean exibida = pesoValido >= h.pesoValidoMinimoArea() - 1e-9;
            Double nota = exibida ? umaCasa(soma / pesoValido) : null;
            parciais.add(new Parcial(area, area.indicadores(), pontos, motivos, medidas, pesoValido, nota));
        }

        double pesoAreasExibidas = parciais.stream().filter(p -> p.nota() != null).mapToDouble(p -> p.area().peso()).sum();
        // Cobertura da nota = soma dos pesos das áreas que aparecem (é a leitura do plano aprovado:
        // entre 28 e 55 dias, Clientes + Operação + Cardápio = 65% ≥ 60%, e a nota provisória aparece).
        double pesoValidoTotal = pesoAreasExibidas;
        double notaBruta = pesoAreasExibidas == 0 ? 0
                : parciais.stream().filter(p -> p.nota() != null).mapToDouble(p -> p.nota() * p.area().peso()).sum() / pesoAreasExibidas;

        List<ResultadoArea> areas = new ArrayList<>();
        List<ResultadoIndicador> indicadores = new ArrayList<>();
        for (Parcial p : parciais) {
            boolean exibida = p.nota() != null;
            areas.add(new ResultadoArea(p.area().codigo(), p.nota(), p.area().peso(), p.pesoValidoArea(), exibida));
            for (int i = 0; i < p.indicadores().size(); i++) {
                RegraScore.Indicador ind = p.indicadores().get(i);
                Medida m = p.medidas().get(i);
                Double pts = p.pontos().get(i);
                double pesoEfetivo = exibida && pts != null
                        ? (p.area().peso() / pesoAreasExibidas) * (ind.peso() / p.pesoValidoArea()) : 0;
                indicadores.add(new ResultadoIndicador(ind.codigo(), p.area().codigo(), m == null ? null : m.valor(), pts,
                        quatroCasas(pesoEfetivo), pts == null ? StatusIndicador.SEM_DADOS : StatusIndicador.OK,
                        p.motivos().get(i), m == null ? 0 : m.amostra()));
            }
        }

        String motivoSemNota = motivoSemNota(h, entrada, pesoValidoTotal);
        Situacao situacao = motivoSemNota != null ? Situacao.COLETANDO
                : entrada.diasHistorico() < h.diasNotaOficial() ? Situacao.PROVISORIA : Situacao.OFICIAL;
        Integer nota = situacao == Situacao.COLETANDO ? null : (int) Math.round(notaBruta);

        return new Resultado(regra.versao(), situacao, nota, nota == null ? null : regra.faixaDaNota(nota).codigo(),
                quatroCasas(pesoValidoTotal), entrada.diasHistorico(), entrada.pedidosConcluidosJanela(), motivoSemNota,
                areas, indicadores);
    }

    /** 0–100 por interpolação linear entre (zero→0), (referência→60) e (cem→100), travado nas pontas. */
    static double pontos(RegraScore.Ancoras a, double valor) {
        boolean maiorMelhor = a.cem() > a.zero();
        double v = maiorMelhor ? valor : -valor;
        double zero = maiorMelhor ? a.zero() : -a.zero();
        double ref = maiorMelhor ? a.referencia() : -a.referencia();
        double cem = maiorMelhor ? a.cem() : -a.cem();
        double p;
        if (v <= zero) p = 0;
        else if (v >= cem) p = 100;
        else if (v <= ref) p = 60 * (v - zero) / (ref - zero);
        else p = 60 + 40 * (v - ref) / (cem - ref);
        return umaCasa(p);
    }

    private static String motivoSemDados(RegraScore.Indicador ind, Medida m, int diasHistorico) {
        RegraScore.Minimos min = ind.minimos();
        if (min != null && min.diasHistorico() != null && diasHistorico < min.diasHistorico()) {
            return "Precisa de " + min.diasHistorico() + " dias de histórico (faltam " + (min.diasHistorico() - diasHistorico) + ").";
        }
        if (m == null) return "Ainda não há dados para medir.";
        if (min != null && min.amostra() != null && m.amostra() < min.amostra()) {
            return "Poucos dados para medir: " + m.amostra() + " de " + min.amostra() + " necessários.";
        }
        if (min != null && min.cobertura() != null && (m.cobertura() == null || m.cobertura() < min.cobertura())) {
            long cobertura = m.cobertura() == null ? 0 : Math.round(m.cobertura() * 100);
            return "Só " + cobertura + "% dos pedidos têm esse horário registrado (mínimo " + Math.round(min.cobertura() * 100) + "%).";
        }
        if (m.valor() == null || m.valor().isNaN()) return "Ainda não há dados para medir.";
        return null;
    }

    private static String motivoSemNota(RegraScore.Historico h, Entrada e, double pesoValido) {
        if (e.diasHistorico() < h.diasMinimosNota()) {
            return "Coletando dados: a nota aparece com " + h.diasMinimosNota() + " dias de histórico (faltam "
                    + (h.diasMinimosNota() - e.diasHistorico()) + ").";
        }
        if (e.pedidosConcluidosJanela() < h.pedidosConcluidosMinimos()) {
            return "Coletando dados: são necessários " + h.pedidosConcluidosMinimos() + " pedidos concluídos nas últimas 4 semanas ("
                    + e.pedidosConcluidosJanela() + " até agora).";
        }
        if (pesoValido < h.pesoValidoMinimo() - 1e-9) {
            return "Ainda não há dados suficientes nos indicadores para uma nota confiável.";
        }
        return null;
    }

    private static double umaCasa(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double quatroCasas(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
