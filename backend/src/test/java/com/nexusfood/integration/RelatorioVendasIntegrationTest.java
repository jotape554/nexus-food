package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.plataforma.enums.Papel;
import com.nexusfood.plataforma.model.Usuario;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatório de vendas com um cenário montado a dedo (horários em UTC; o restaurante é de
 * São Paulo, UTC-3, com virada do dia às 04:00):
 *
 *   20/08 12:00  cliente Y  retirada  A        50,00  concluído   (mês anterior)
 *   14/09 12:00  cliente X  retirada  A        50,00  concluído
 *   16/09 20:00  cliente Y  entrega   A+2B+5   95,00  concluído
 *   21/09 12:30  cliente X  retirada  B        20,00  concluído
 *   21/09 13:00  cliente Z  retirada  A        50,00  cancelado pelo restaurante
 *   22/09 01:30  cliente Z  entrega   B+5      25,00  concluído   → conta no dia 21 (madrugada)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class RelatorioVendasIntegrationTest {

    private static final String TEL_X = "11911110001";
    private static final String TEL_Y = "11911110002";
    private static final String TEL_Z = "11911110003";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RelogioDeTeste relogio;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RestauranteRepository restauranteRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
        relogio.definir(RelogioDeTesteConfig.INICIO);
    }

    private Sessao montarCenario(String email) throws Exception {
        Sessao s = api.registrar(email);
        api.abrirLoja(s, "5.00");
        long cat = api.criarCategoria(s, "Pratos");
        long a = api.criarProduto(s, cat, "Produto A", "50.00");
        long b = api.criarProduto(s, cat, "Produto B", "20.00");
        String itemA = "[{\"produtoId\":%d,\"quantidade\":1}]".formatted(a);
        String itemB = "[{\"produtoId\":%d,\"quantidade\":1}]".formatted(b);

        pedidoEm(s, "2026-08-20T15:00:00Z", TEL_Y, "RETIRADA", "PIX", itemA, true);
        pedidoEm(s, "2026-09-14T15:00:00Z", TEL_X, "RETIRADA", "PIX", itemA, true);
        pedidoEm(s, "2026-09-16T23:00:00Z", TEL_Y, "ENTREGA", "DINHEIRO",
                "[{\"produtoId\":%d,\"quantidade\":1},{\"produtoId\":%d,\"quantidade\":2}]".formatted(a, b), true);
        pedidoEm(s, "2026-09-21T15:30:00Z", TEL_X, "RETIRADA", "PIX", itemB, true);
        relogio.definir(Instant.parse("2026-09-21T16:00:00Z"));
        api.cancelar(s, api.pedir(s, TEL_Z, "RETIRADA", "PIX", itemA), "ITEM_INDISPONIVEL");
        pedidoEm(s, "2026-09-22T04:30:00Z", TEL_Z, "ENTREGA", "CARTAO_CREDITO", itemB, true);
        return s;
    }

    private void pedidoEm(Sessao s, String instante, String tel, String mod, String pag, String itens, boolean concluir) throws Exception {
        relogio.definir(Instant.parse(instante));
        long id = api.pedir(s, tel, mod, pag, itens);
        if (concluir) api.concluir(s, id);
    }

    private JsonNode relatorio(Sessao s, String query) throws Exception {
        return api.json(api.chamar(s, get("/api/relatorios/vendas?" + query), null).andExpect(status().isOk()));
    }

    @Test
    void relatorioDiarioComTotaisSerieEDistribuicoes() throws Exception {
        Sessao s = montarCenario("rel-dia@teste.com");
        JsonNode r = relatorio(s, "inicio=2026-09-14&fim=2026-09-21&agrupamento=DIA");

        JsonNode resumo = r.get("resumo");
        assertThat(resumo.get("faturamento").decimalValue()).isEqualByComparingTo("190.00");
        assertThat(resumo.get("pedidosConcluidos").asLong()).isEqualTo(4);
        assertThat(resumo.get("pedidosRecebidos").asLong()).isEqualTo(5);
        assertThat(resumo.get("ticketMedio").decimalValue()).isEqualByComparingTo("47.50");
        assertThat(resumo.get("cancelados").asLong()).isEqualTo(1);
        assertThat(resumo.get("canceladosPeloRestaurante").asLong()).isEqualTo(1);
        assertThat(resumo.get("taxaCancelamento").decimalValue()).isEqualByComparingTo("20.0");
        assertThat(resumo.get("itensVendidos").asLong()).isEqualTo(6);
        assertThat(resumo.get("clientesUnicos").asLong()).isEqualTo(3);
        assertThat(resumo.get("clientesNovos").asLong()).as("Y já tinha comprado em agosto").isEqualTo(2);
        assertThat(resumo.get("clientesRecorrentes").asLong()).isEqualTo(1);

        JsonNode serie = r.get("serie");
        assertThat(serie).as("um ponto por dia, inclusive sem venda").hasSize(8);
        assertThat(serie.get(0).get("faturamento").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(serie.get(1).get("faturamento").decimalValue()).isEqualByComparingTo("0");
        assertThat(serie.get(2).get("faturamento").decimalValue()).isEqualByComparingTo("95.00");
        assertThat(serie.get(7).get("inicio").asText()).isEqualTo("2026-09-21");
        assertThat(serie.get(7).get("faturamento").decimalValue()).as("inclui a venda da madrugada de 22").isEqualByComparingTo("45.00");
        assertThat(serie.get(7).get("pedidosConcluidos").asLong()).isEqualTo(2);
        assertThat(serie.get(7).get("cancelados").asLong()).isEqualTo(1);

        assertThat(r.get("porModalidade").get(0).get("chave").asText()).isEqualTo("ENTREGA");
        assertThat(r.get("porModalidade").get(0).get("faturamento").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(r.get("porModalidade").get(0).get("percentualFaturamento").decimalValue()).isEqualByComparingTo("63.2");
        assertThat(r.get("porFormaPagamento").get(0).get("chave").asText()).isEqualTo("DINHEIRO");

        JsonNode porHora = r.get("porHora");
        assertThat(porHora).hasSize(24);
        assertThat(porHora.get(12).get("pedidos").asLong()).isEqualTo(2);
        assertThat(porHora.get(1).get("faturamento").decimalValue()).isEqualByComparingTo("25.00");

        assertThat(r.get("porDiaDaSemana").get(0).get("pedidos").asLong()).as("segunda").isEqualTo(3);
        assertThat(r.get("porDiaDaSemana").get(2).get("pedidos").asLong()).as("quarta").isEqualTo(1);

        JsonNode produtos = r.get("produtos");
        assertThat(produtos.get(0).get("nome").asText()).isEqualTo("Produto A");
        assertThat(produtos.get(0).get("quantidade").asLong()).isEqualTo(2);
        assertThat(produtos.get(1).get("quantidade").asLong()).isEqualTo(4);
        assertThat(produtos.get(1).get("faturamento").decimalValue()).isEqualByComparingTo("80.00");

        JsonNode comparacao = r.get("comparacao");
        assertThat(comparacao.get("inicio").asText()).isEqualTo("2026-09-06");
        assertThat(comparacao.get("fim").asText()).isEqualTo("2026-09-13");
        assertThat(comparacao.get("variacaoFaturamento").isNull()).as("sem venda antes, sem variação").isTrue();
    }

    @Test
    void agrupaPorSemanaDeSegundaADomingoRecortadaNoPeriodo() throws Exception {
        Sessao s = montarCenario("rel-semana@teste.com");
        JsonNode serie = relatorio(s, "inicio=2026-09-14&fim=2026-09-21&agrupamento=SEMANA").get("serie");

        assertThat(serie).hasSize(2);
        assertThat(serie.get(0).get("inicio").asText()).isEqualTo("2026-09-14");
        assertThat(serie.get(0).get("fim").asText()).isEqualTo("2026-09-20");
        assertThat(serie.get(0).get("faturamento").decimalValue()).isEqualByComparingTo("145.00");
        assertThat(serie.get(1).get("fim").asText()).as("recortada no fim do período").isEqualTo("2026-09-21");
        assertThat(serie.get(1).get("faturamento").decimalValue()).isEqualByComparingTo("45.00");
    }

    @Test
    void agrupaPorMesEComparaComOsMesesAnteriores() throws Exception {
        Sessao s = montarCenario("rel-mes@teste.com");
        JsonNode r = relatorio(s, "inicio=2026-08-01&fim=2026-09-30&agrupamento=MES");

        assertThat(r.get("serie")).hasSize(2);
        assertThat(r.get("serie").get(0).get("faturamento").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(r.get("serie").get(1).get("faturamento").decimalValue()).isEqualByComparingTo("190.00");
        assertThat(r.get("comparacao").get("inicio").asText()).isEqualTo("2026-06-01");
        assertThat(r.get("comparacao").get("fim").asText()).isEqualTo("2026-07-31");

        JsonNode setembro = relatorio(s, "inicio=2026-09-01&fim=2026-09-30&agrupamento=MES");
        assertThat(setembro.get("comparacao").get("faturamento").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(setembro.get("comparacao").get("variacaoFaturamento").decimalValue()).isEqualByComparingTo("280.0");
    }

    @Test
    void semParametrosMostraOsUltimos30DiasPorDia() throws Exception {
        Sessao s = api.registrar("rel-padrao@teste.com");
        JsonNode r = relatorio(s, "");
        assertThat(r.get("hoje").asText()).isEqualTo("2026-09-23");
        assertThat(r.get("fim").asText()).isEqualTo("2026-09-23");
        assertThat(r.get("inicio").asText()).isEqualTo("2026-08-25");
        assertThat(r.get("agrupamento").asText()).isEqualTo("DIA");
        assertThat(r.get("serie")).hasSize(30);
        assertThat(r.get("resumo").get("faturamento").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void periodoInvalidoEMensagemClara() throws Exception {
        Sessao s = api.registrar("rel-invalido@teste.com");
        api.chamar(s, get("/api/relatorios/vendas?inicio=2026-09-10&fim=2026-09-01"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data final não pode ser anterior à data inicial."));
        api.chamar(s, get("/api/relatorios/vendas?inicio=2025-01-01&fim=2026-09-01"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Escolha um período de até 1 ano."));
    }

    @Test
    void outroRestauranteNaoVeAsVendas() throws Exception {
        montarCenario("rel-dono@teste.com");
        Sessao outro = api.registrar("rel-outro@teste.com");
        JsonNode r = relatorio(outro, "inicio=2026-09-14&fim=2026-09-21");
        assertThat(r.get("resumo").get("pedidosRecebidos").asLong()).isZero();
        assertThat(r.get("produtos")).isEmpty();
    }

    @Test
    void atendenteNaoVeFaturamentoMasOperaPedidos() throws Exception {
        Sessao dono = api.registrar("rel-equipe@teste.com");
        var restaurante = usuarioRepository.findByEmail("rel-equipe@teste.com").orElseThrow().getRestaurante();
        usuarioRepository.save(Usuario.builder()
                .nome("Atendente").email("atendente-rel@teste.com").senhaHash(passwordEncoder.encode("123456"))
                .papel(Papel.ATENDENTE).restaurante(restauranteRepository.findById(restaurante.getId()).orElseThrow())
                .build());
        String token = api.json(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"atendente-rel@teste.com\",\"senha\":\"123456\"}"))).get("token").asText();
        Sessao atendente = new Sessao(token, dono.slug());

        api.chamar(atendente, get("/api/relatorios/vendas"), null).andExpect(status().isForbidden());
        api.chamar(atendente, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());
    }
}
