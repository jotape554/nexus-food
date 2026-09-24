package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.analytics.repository.NexusInsightRepository;
import com.nexusfood.analytics.repository.NexusScoreRepository;
import com.nexusfood.analytics.repository.SnapshotDiarioRepository;
import com.nexusfood.analytics.service.NexusJob;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.support.ApiDeTeste;
import com.nexusfood.support.ApiDeTeste.Sessao;
import com.nexusfood.support.RelogioDeTeste;
import com.nexusfood.support.RelogioDeTesteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nexus Score de ponta a ponta, com um cenário de nota conhecida.
 *
 * "Hoje" = 23/09/2026 → dia de referência D = 22/09. 60 dias de pedidos (25/07 a 22/09), 3 por
 * dia ao meio-dia, de 40 clientes em rodízio. Janela J = 26/08–22/09; anterior J-1 = 29/07–25/08.
 *   Produto A: R$ 50 em J-1 e R$ 55 em J. Produto B (R$ 20) nunca vende.
 *   Aceite em 2 min, pronto em 20 min (prometido: 30). Em J, 1 recusa por semana (item indisponível).
 *
 *   V1 faturamento  4.620 vs 4.200 = +10%   → 86,7     C1 recompra 40/40 = 100%    → 100
 *   V2 ticket       55 vs 50 = +10%         → 100      C2 retenção 28/28 = 100%    → 100
 *   V3 regularidade 4 semanas iguais, CV 0  → 100      C3 base 40 vs 40 = 0%       → 60
 *   O1 cancelamento 4/88 = 4,5%             → 66,1     K1 só 2 produtos (< 5)      → sem dados
 *   O2 aceite 2 min → 100    O3 pontualidade 100% → 100
 *
 *   Vendas 93,4 · Clientes 92,0 · Operação 84,7 · Cardápio não aparece
 *   Nota = (93,4×0,35 + 92×0,30 + 84,7×0,25) ÷ 0,90 = 90,5 → 91 (Excelente)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class NexusIntegrationTest {

    private static final LocalDate PRIMEIRO_DIA = LocalDate.of(2026, 7, 25);
    private static final LocalDate DIA_REFERENCIA = LocalDate.of(2026, 9, 22);
    private static final LocalDate INICIO_JANELA = LocalDate.of(2026, 8, 26);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RelogioDeTeste relogio;
    @Autowired private NexusJob nexusJob;
    @Autowired private NexusScoreRepository scoreRepository;
    @Autowired private NexusInsightRepository insightRepository;
    @Autowired private SnapshotDiarioRepository snapshotRepository;
    @Autowired private RestauranteRepository restauranteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
        relogio.definir(RelogioDeTesteConfig.INICIO);
    }

    /** Meio-dia (horário de SP = 15:00 UTC) do dia, mais alguns minutos. */
    private static Instant meioDia(LocalDate dia, int minutos) {
        return Instant.parse(dia + "T15:00:00Z").plus(Duration.ofMinutes(minutos));
    }

    private Sessao montarCenario(String email) throws Exception {
        relogio.definir(meioDia(PRIMEIRO_DIA, -60));
        Sessao s = api.registrar(email);
        assinaturaAtiva(email); // o cenário atravessa 2 meses: o teste grátis de 14 dias venceria no meio
        api.abrirLoja(s, "0");
        long cat = api.criarCategoria(s, "Pratos");
        long a = api.criarProduto(s, cat, "Produto A", "50.00");
        api.criarProduto(s, cat, "Produto B", "20.00");
        String itemA = "[{\"produtoId\":%d,\"quantidade\":1}]".formatted(a);

        int n = 0;
        for (LocalDate dia = PRIMEIRO_DIA; !dia.isAfter(DIA_REFERENCIA); dia = dia.plusDays(1)) {
            if (dia.equals(INICIO_JANELA)) {
                relogio.definir(meioDia(dia, -30));
                api.chamar(s, put("/api/produtos/" + a), "{\"categoriaId\":%d,\"nome\":\"Produto A\",\"preco\":55.00}".formatted(cat))
                        .andExpect(status().isOk());
            }
            for (int k = 0; k < 3; k++, n++) {
                Instant t = meioDia(dia, k * 10);
                String telefone = "1197777%04d".formatted(n % 40);
                relogio.definir(t);
                long id = api.pedir(s, telefone, "RETIRADA", "PIX", itemA);
                mover(s, id, "CONFIRMADO", t.plus(Duration.ofMinutes(2)));
                mover(s, id, "PRONTO", t.plus(Duration.ofMinutes(20)));
                mover(s, id, "CONCLUIDO", t.plus(Duration.ofMinutes(25)));
            }
            if (!dia.isBefore(INICIO_JANELA) && (dia.toEpochDay() - INICIO_JANELA.toEpochDay()) % 7 == 0) {
                Instant t = meioDia(dia, 60);
                relogio.definir(t);
                long id = api.pedir(s, "11977770999", "RETIRADA", "PIX", itemA);
                relogio.definir(t.plus(Duration.ofMinutes(3)));
                api.cancelar(s, id, "ITEM_INDISPONIVEL");
            }
        }
        relogio.definir(RelogioDeTesteConfig.INICIO);
        return s;
    }

    private void assinaturaAtiva(String email) {
        var restaurante = restauranteRepository.findById(
                usuarioRepository.findByEmail(email).orElseThrow().getRestaurante().getId()).orElseThrow();
        restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
        restauranteRepository.save(restaurante);
    }

    private void mover(Sessao s, long id, String status, Instant quando) throws Exception {
        relogio.definir(quando);
        api.chamar(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"%s\"}".formatted(status)).andExpect(status().isOk());
    }

    private JsonNode indicador(JsonNode nexus, String codigo) {
        for (JsonNode area : nexus.get("areas")) {
            for (JsonNode ind : area.get("indicadores")) {
                if (ind.get("codigo").asText().equals(codigo)) return ind;
            }
        }
        throw new AssertionError("Indicador ausente: " + codigo);
    }

    @Test
    void notaConhecidaComCadaIndicadorEInsights() throws Exception {
        Sessao s = montarCenario("nexus-cenario@teste.com");
        JsonNode r = api.json(api.chamar(s, get("/api/nexus"), null).andExpect(status().isOk()));

        assertThat(r.get("dia").asText()).isEqualTo("2026-09-22");
        assertThat(r.get("inicioJanela").asText()).isEqualTo("2026-08-26");
        assertThat(r.get("regraVersao").asText()).isEqualTo("v1");
        assertThat(r.get("situacao").asText()).isEqualTo("OFICIAL");
        assertThat(r.get("diasHistorico").asInt()).isEqualTo(60);
        assertThat(r.get("pedidosConcluidosJanela").asInt()).isEqualTo(84);

        assertThat(indicador(r, "V1_CRESCIMENTO_FATURAMENTO").get("valor").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(indicador(r, "V1_CRESCIMENTO_FATURAMENTO").get("pontos").decimalValue()).isEqualByComparingTo("86.7");
        assertThat(indicador(r, "V2_EVOLUCAO_TICKET").get("pontos").decimalValue()).isEqualByComparingTo("100");
        assertThat(indicador(r, "V3_REGULARIDADE").get("valor").decimalValue()).isEqualByComparingTo("0");
        assertThat(indicador(r, "C1_RECOMPRA").get("valor").decimalValue()).isEqualByComparingTo("100");
        assertThat(indicador(r, "C1_RECOMPRA").get("amostra").asLong()).isEqualTo(40);
        assertThat(indicador(r, "C2_RETENCAO_NOVOS").get("amostra").asLong()).isEqualTo(28);
        assertThat(indicador(r, "C2_RETENCAO_NOVOS").get("valor").decimalValue()).isEqualByComparingTo("100");
        assertThat(indicador(r, "C3_BASE_ATIVA").get("pontos").decimalValue()).isEqualByComparingTo("60");
        assertThat(indicador(r, "O1_CANCELAMENTO_RESTAURANTE").get("valor").decimalValue()).isEqualByComparingTo("4.55");
        assertThat(indicador(r, "O1_CANCELAMENTO_RESTAURANTE").get("pontos").decimalValue()).isEqualByComparingTo("66.1");
        assertThat(indicador(r, "O2_TEMPO_ACEITE").get("valor").decimalValue()).isEqualByComparingTo("2");
        assertThat(indicador(r, "O3_PONTUALIDADE").get("valor").decimalValue()).isEqualByComparingTo("100");
        JsonNode k1 = indicador(r, "K1_PRODUTOS_PARADOS");
        assertThat(k1.get("status").asText()).isEqualTo("SEM_DADOS");
        assertThat(k1.get("motivo").asText()).contains("2 de 5");

        assertThat(r.get("areas").get(0).get("nota").decimalValue()).isEqualByComparingTo("93.4");
        assertThat(r.get("areas").get(1).get("nota").decimalValue()).isEqualByComparingTo("92.0");
        assertThat(r.get("areas").get(2).get("nota").decimalValue()).isEqualByComparingTo("84.7");
        assertThat(r.get("areas").get(3).get("exibida").asBoolean()).isFalse();
        assertThat(r.get("nota").asInt()).isEqualTo(91);
        assertThat(r.get("faixa").asText()).isEqualTo("EXCELENTE");
        assertThat(r.get("faixaNome").asText()).isEqualTo("Excelente");

        List<String> textos = new ArrayList<>();
        r.get("insights").forEach(i -> textos.add(i.get("severidade").asText() + " " + i.get("texto").asText()));
        assertThat(textos).containsExactly(
                "OPORTUNIDADE 1 produto não vendeu nenhuma unidade nas últimas 4 semanas: Produto B. Vale revisar o preço, a descrição ou tirar do cardápio.",
                "OPORTUNIDADE 100% dos seus pedidos chegam entre 12h e 14h. Garanta equipe e estoque nesse horário.",
                "CONQUISTA Seu faturamento cresceu 10% nas últimas 4 semanas em relação às 4 anteriores (R$ 4.200,00 → R$ 4.620,00).",
                "CONQUISTA 100% dos clientes das últimas 4 semanas já tinham comprado antes: sua clientela está voltando.");

        // Evolução: notas desde o 28º dia de histórico; no começo ainda coletando, no fim oficial.
        JsonNode historico = r.get("historico");
        assertThat(historico).hasSize(33);
        assertThat(historico.get(0).get("dia").asText()).isEqualTo("2026-08-21");
        assertThat(historico.get(0).get("situacao").asText()).as("28 dias, mas Vendas e Cardápio ainda sem dados").isEqualTo("COLETANDO");
        assertThat(historico.get(32).get("situacao").asText()).isEqualTo("OFICIAL");
        assertThat(r.get("variacao7Dias").isNull() || r.get("variacao7Dias").isInt()).isTrue();
    }

    @Test
    void jobEIdempotenteENaoDuplicaNada() throws Exception {
        Sessao s = montarCenario("nexus-job@teste.com");
        nexusJob.executar();
        long notas = scoreRepository.count();
        long insights = insightRepository.count();
        long resumos = snapshotRepository.count();

        nexusJob.executar();
        api.json(api.chamar(s, get("/api/nexus"), null).andExpect(status().isOk()));

        assertThat(scoreRepository.count()).isEqualTo(notas);
        assertThat(insightRepository.count()).isEqualTo(insights);
        assertThat(snapshotRepository.count()).isEqualTo(resumos);
    }

    @Test
    void restauranteNovoAindaColetandoDados() throws Exception {
        Sessao s = api.registrar("nexus-novo@teste.com");
        JsonNode r = api.json(api.chamar(s, get("/api/nexus"), null).andExpect(status().isOk()));
        assertThat(r.get("situacao").asText()).isEqualTo("COLETANDO");
        assertThat(r.get("nota").isNull()).isTrue();
        assertThat(r.get("diasHistorico").asInt()).isZero();
        assertThat(r.get("diasParaNota").asInt()).isEqualTo(28);
        assertThat(r.get("motivoSemNota").asText()).contains("28 dias");
        assertThat(r.get("insights")).isEmpty();
    }
}
