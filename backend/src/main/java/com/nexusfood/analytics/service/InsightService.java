package com.nexusfood.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.analytics.model.NexusInsight;
import com.nexusfood.analytics.model.NexusInsight.Severidade;
import com.nexusfood.analytics.repository.NexusInsightRepository;
import com.nexusfood.analytics.service.ColetorIndicadores.Coleta;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Insights por modelo de texto. Cada modelo tem condição, severidade e prioridade; o texto é
 * montado com números da coleta, então sempre dá para conferir. Sem IA escrevendo texto.
 *
 * Regras: um modelo não se repete dentro de 7 dias a menos que o número piore; a tela mostra
 * no máximo 5, um por modelo, alertas primeiro.
 */
@Service
@RequiredArgsConstructor
public class InsightService {

    public static final int DIAS_SEM_REPETIR = 7;
    public static final int MAXIMO_NA_TELA = 5;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final NexusInsightRepository insightRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Um insight candidato: número principal (maior = mais relevante), parâmetros e texto. */
    record Candidato(double valor, Map<String, Object> parametros, String texto) {}

    /** Um modelo de texto do catálogo. Mudou o texto ou a condição: suba a versão. */
    record Modelo(String id, int versao, Severidade severidade, int prioridade, Function<Coleta, Optional<Candidato>> avaliar) {}

    static final List<Modelo> CATALOGO = List.of(
            new Modelo("FATURAMENTO_QUEDA", 1, Severidade.ALERTA, 10, c -> {
                Double v1 = c.valor("V1_CRESCIMENTO_FATURAMENTO");
                if (v1 == null || v1 > -10) return Optional.empty();
                return Optional.of(new Candidato(-v1, Map.of("percentual", arred(-v1), "antes", c.faturamentoAnterior(), "agora", c.faturamentoJanela()),
                        "Seu faturamento caiu " + pct(-v1) + " nas últimas 4 semanas em relação às 4 anteriores ("
                                + moeda(c.faturamentoAnterior()) + " → " + moeda(c.faturamentoJanela()) + ")."));
            }),
            new Modelo("CANCELAMENTO_ALTO", 1, Severidade.ALERTA, 20, c -> {
                Double o1 = c.valor("O1_CANCELAMENTO_RESTAURANTE");
                long amostra = c.entrada().medidas().get("O1_CANCELAMENTO_RESTAURANTE").amostra();
                if (o1 == null || o1 <= 5 || amostra < 30) return Optional.empty();
                String motivo = c.motivoCancelamentoMaisComum() == null ? null : rotulo(c.motivoCancelamentoMaisComum());
                return Optional.of(new Candidato(o1, parametros("percentual", arred(o1), "motivo", motivo),
                        pct(o1) + " dos pedidos das últimas 4 semanas foram cancelados pelo restaurante"
                                + (motivo == null ? "." : ". Motivo mais comum: " + motivo.toLowerCase(PT_BR) + ".")));
            }),
            new Modelo("ACEITE_LENTO", 1, Severidade.ALERTA, 30, c -> {
                Double o2 = c.valor("O2_TEMPO_ACEITE");
                long amostra = c.entrada().medidas().get("O2_TEMPO_ACEITE").amostra();
                if (o2 == null || o2 <= 5 || amostra < 30) return Optional.empty();
                long minutos = Math.round(o2);
                return Optional.of(new Candidato(o2, Map.of("minutos", minutos),
                        "Metade dos pedidos esperou mais de " + minutos + " min para ser aceita. O ideal é aceitar em até 5 min."));
            }),
            new Modelo("PRODUTOS_PARADOS", 1, Severidade.OPORTUNIDADE, 40, c -> {
                List<String> parados = c.produtosParados();
                if (parados.isEmpty()) return Optional.empty();
                String lista = parados.size() <= 3 ? juntar(parados)
                        : String.join(", ", parados.subList(0, 3)) + " e mais " + (parados.size() - 3);
                String sujeito = parados.size() == 1 ? "1 produto não vendeu" : parados.size() + " produtos não venderam";
                return Optional.of(new Candidato(parados.size(), Map.of("quantidade", parados.size(), "produtos", parados),
                        sujeito + " nenhuma unidade nas últimas 4 semanas: " + lista + ". Vale revisar o preço, a descrição ou tirar do cardápio."));
            }),
            new Modelo("HORARIO_PICO", 1, Severidade.OPORTUNIDADE, 50, c -> {
                long[] horas = c.pedidosPorHora();
                long total = Arrays.stream(horas).sum();
                if (total < 30) return Optional.empty();
                int melhor = 0;
                long maior = -1;
                // Melhor bloco de 2 horas; no empate, o que começa numa hora com mais pedidos
                // (pedidos todos às 12h viram "12h e 14h", não "11h e 13h").
                for (int h = 0; h < 24; h++) {
                    long bloco = horas[h] + horas[(h + 1) % 24];
                    if (bloco > maior || (bloco == maior && horas[h] > horas[melhor])) { maior = bloco; melhor = h; }
                }
                double parte = maior * 100.0 / total;
                if (parte < 25) return Optional.empty();
                int fim = (melhor + 2) % 24;
                return Optional.of(new Candidato(parte, Map.of("percentual", arred(parte), "inicio", melhor, "fim", fim),
                        pct(parte) + " dos seus pedidos chegam entre " + melhor + "h e " + fim + "h. Garanta equipe e estoque nesse horário."));
            }),
            new Modelo("FATURAMENTO_ALTA", 1, Severidade.CONQUISTA, 60, c -> {
                Double v1 = c.valor("V1_CRESCIMENTO_FATURAMENTO");
                if (v1 == null || v1 < 10) return Optional.empty();
                return Optional.of(new Candidato(v1, Map.of("percentual", arred(v1), "antes", c.faturamentoAnterior(), "agora", c.faturamentoJanela()),
                        "Seu faturamento cresceu " + pct(v1) + " nas últimas 4 semanas em relação às 4 anteriores ("
                                + moeda(c.faturamentoAnterior()) + " → " + moeda(c.faturamentoJanela()) + ")."));
            }),
            new Modelo("PRODUTO_EM_ALTA", 1, Severidade.CONQUISTA, 70, c -> {
                return c.vendidosJanela().entrySet().stream()
                        .filter(e -> e.getValue() >= 10 && c.vendidosAnterior().getOrDefault(e.getKey(), 0L) > 0)
                        .map(e -> Map.entry(e.getKey(), (e.getValue() * 100.0 / c.vendidosAnterior().get(e.getKey())) - 100))
                        .filter(e -> e.getValue() >= 30)
                        .max(Map.Entry.comparingByValue())
                        .map(e -> {
                            String nome = c.nomesProdutos().getOrDefault(e.getKey(), "Um produto");
                            long antes = c.vendidosAnterior().get(e.getKey());
                            long agora = c.vendidosJanela().get(e.getKey());
                            return new Candidato(e.getValue(), Map.of("produto", nome, "percentual", arred(e.getValue()), "antes", antes, "agora", agora),
                                    nome + " vendeu " + pct(e.getValue()) + " a mais que nas 4 semanas anteriores (" + antes + " → " + agora + " unidades).");
                        });
            }),
            new Modelo("RECOMPRA_BOA", 1, Severidade.CONQUISTA, 80, c -> {
                Double c1 = c.valor("C1_RECOMPRA");
                long amostra = c.entrada().medidas().get("C1_RECOMPRA").amostra();
                if (c1 == null || c1 < 40 || amostra < 30) return Optional.empty();
                return Optional.of(new Candidato(c1, Map.of("percentual", arred(c1)),
                        pct(c1) + " dos clientes das últimas 4 semanas já tinham comprado antes: sua clientela está voltando."));
            })
    );

    /** Gera os insights do dia (substituindo os do mesmo dia, se já existirem). */
    public void gerar(Long restauranteId, Coleta coleta) {
        LocalDate dia = coleta.dia();
        insightRepository.apagarDia(restauranteId, dia);
        Map<String, NexusInsight> ultimos = ultimoPorModelo(
                insightRepository.findAllByRestauranteIdAndDiaBetweenOrderByDiaDescIdDesc(restauranteId, dia.minusDays(DIAS_SEM_REPETIR), dia.minusDays(1)));

        Instant agora = Instant.now(clock);
        for (Modelo modelo : CATALOGO) {
            modelo.avaliar().apply(coleta).ifPresent(candidato -> {
                NexusInsight anterior = ultimos.get(modelo.id());
                boolean jaAvisado = anterior != null && anterior.getValorReferencia() != null
                        && candidato.valor() <= anterior.getValorReferencia().doubleValue();
                if (jaAvisado) return;
                insightRepository.save(NexusInsight.builder()
                        .restauranteId(restauranteId)
                        .dia(dia)
                        .template(modelo.id())
                        .templateVersao(modelo.versao())
                        .severidade(modelo.severidade())
                        .prioridade(modelo.prioridade())
                        .valorReferencia(BigDecimal.valueOf(candidato.valor()).setScale(4, java.math.RoundingMode.HALF_UP))
                        .parametros(json(candidato.parametros()))
                        .texto(candidato.texto())
                        .criadoEm(agora)
                        .build());
            });
        }
    }

    /** Insights ativos: dos últimos 7 dias, o mais recente de cada modelo, alertas primeiro, no máximo 5. */
    public List<NexusInsight> ativos(Long restauranteId, LocalDate dia) {
        return ultimoPorModelo(insightRepository.findAllByRestauranteIdAndDiaBetweenOrderByDiaDescIdDesc(
                restauranteId, dia.minusDays(DIAS_SEM_REPETIR - 1L), dia)).values().stream()
                .sorted(Comparator.comparing(NexusInsight::getSeveridade).thenComparingInt(NexusInsight::getPrioridade))
                .limit(MAXIMO_NA_TELA)
                .toList();
    }

    private static Map<String, NexusInsight> ultimoPorModelo(List<NexusInsight> maisRecentesPrimeiro) {
        return maisRecentesPrimeiro.stream().collect(Collectors.toMap(NexusInsight::getTemplate, i -> i, (a, b) -> a, LinkedHashMap::new));
    }

    private String json(Map<String, Object> parametros) {
        try {
            return objectMapper.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parâmetros de insight inválidos", e);
        }
    }

    private static Map<String, Object> parametros(Object... chaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            if (chaveValor[i + 1] != null) mapa.put((String) chaveValor[i], chaveValor[i + 1]);
        }
        return mapa;
    }

    private static double arred(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String pct(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(PT_BR);
        f.setMaximumFractionDigits(Math.abs(v) < 10 ? 1 : 0);
        return f.format(v) + "%";
    }

    private static String moeda(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(v).replace(' ', ' ');
    }

    private static String juntar(List<String> nomes) {
        if (nomes.size() == 1) return nomes.get(0);
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }

    private static String rotulo(MotivoCancelamento motivo) {
        return switch (motivo) {
            case RECUSADO_PELO_RESTAURANTE -> "Recusado pelo restaurante";
            case ITEM_INDISPONIVEL -> "Item indisponível";
            case FORA_DA_AREA_DE_ENTREGA -> "Fora da área de entrega";
            case CLIENTE_DESISTIU -> "Cliente desistiu";
            case NAO_ENTREGUE -> "Não foi possível entregar";
            case OUTRO -> "Outro motivo";
        };
    }
}
