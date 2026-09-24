package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.support.ApiDeTeste;
import com.nexusfood.support.ApiDeTeste.Sessao;
import com.nexusfood.support.RelogioDeTesteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adicionais e variações: cadastro dos grupos, preço sempre calculado no servidor a partir das
 * opções escolhidas, regras de mínimo/máximo, opção esgotada e cópia no pedido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class OpcoesProdutoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
    }

    private static final String OPCOES_PIZZA = """
            {"grupos":[
              {"nome":"Tamanho","minimo":1,"maximo":1,"opcoes":[{"nome":"Média","preco":0},{"nome":"Grande","preco":14}]},
              {"nome":"Borda","minimo":0,"maximo":1,"opcoes":[{"nome":"Catupiry","preco":8},{"nome":"Cheddar","preco":8}]},
              {"nome":"Adicionais","minimo":0,"maximo":3,"cobranca":"SOMA","opcoes":[{"nome":"Bacon","preco":6},{"nome":"Azeitona","preco":3}]}
            ]}""";

    private record Cenario(Sessao s, long pizzaId, JsonNode produto) {
        long opcao(String grupo, String nome) {
            for (JsonNode g : produto.get("grupos")) {
                if (!g.get("nome").asText().equals(grupo)) continue;
                for (JsonNode o : g.get("opcoes")) if (o.get("nome").asText().equals(nome)) return o.get("id").asLong();
            }
            throw new IllegalArgumentException(grupo + "/" + nome);
        }
    }

    private Cenario pizzaria(String email) throws Exception {
        Sessao s = api.registrar(email);
        api.abrirLoja(s, "0");
        long pizzas = api.criarCategoria(s, "Pizzas");
        long margherita = api.criarProduto(s, pizzas, "Margherita", "49.90");
        JsonNode produto = api.json(api.chamar(s, put("/api/produtos/" + margherita + "/opcoes"), OPCOES_PIZZA).andExpect(status().isOk()));
        return new Cenario(s, margherita, produto);
    }

    private String item(long produtoId, int quantidade, long... opcoes) {
        List<String> ids = new ArrayList<>();
        for (long o : opcoes) ids.add(String.valueOf(o));
        return "{\"produtoId\":%d,\"quantidade\":%d,\"opcoes\":[%s]}".formatted(produtoId, quantidade, String.join(",", ids));
    }

    private ResultActions pedirPublico(Sessao s, String itensJson) throws Exception {
        return mockMvc.perform(post("/public/restaurantes/{slug}/pedidos", s.slug())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"chaveIdempotencia":"%s","nomeCliente":"Ana","telefoneCliente":"11987654321",
                         "modalidade":"RETIRADA","formaPagamento":"PIX","itens":%s}
                        """.formatted(UUID.randomUUID(), itensJson)));
    }

    @Test
    void cadastroDosGruposAparecemNoCardapioPublico() throws Exception {
        Cenario c = pizzaria("op-cadastro@teste.com");
        assertThat(c.produto().get("grupos")).hasSize(3);
        assertThat(c.produto().get("precoMinimo").decimalValue()).isEqualByComparingTo("49.90");

        JsonNode cardapio = api.json(mockMvc.perform(get("/public/restaurantes/{slug}/cardapio", c.s().slug())));
        JsonNode grupos = cardapio.get("categorias").get(0).get("produtos").get(0).get("grupos");
        assertThat(grupos.get(0).get("nome").asText()).isEqualTo("Tamanho");
        assertThat(grupos.get(0).get("minimo").asInt()).isEqualTo(1);
        assertThat(grupos.get(2).get("cobranca").asText()).isEqualTo("SOMA");
        assertThat(grupos.get(1).get("opcoes").get(0).get("preco").decimalValue()).isEqualByComparingTo("8");
    }

    @Test
    void precoDoItemSaiDasOpcoesEscolhidasEFicaCopiadoNoPedido() throws Exception {
        Cenario c = pizzaria("op-preco@teste.com");
        long id = api.pedir(c.s(), "11987650001", "RETIRADA", "PIX", "[" + item(c.pizzaId(), 2,
                c.opcao("Tamanho", "Grande"), c.opcao("Borda", "Catupiry"), c.opcao("Adicionais", "Bacon"), c.opcao("Adicionais", "Azeitona")) + "]");

        JsonNode pedido = api.json(api.chamar(c.s(), get("/api/pedidos/" + id), null));
        JsonNode it = pedido.get("itens").get(0);
        // 49,90 + 14 (grande) + 8 (catupiry) + 6 + 3 (adicionais)
        assertThat(it.get("precoUnitario").decimalValue()).isEqualByComparingTo("80.90");
        assertThat(it.get("subtotal").decimalValue()).isEqualByComparingTo("161.80");
        assertThat(pedido.get("total").decimalValue()).isEqualByComparingTo("161.80");
        assertThat(it.get("opcoes")).hasSize(4);
        assertThat(it.get("opcoes").get(0).get("grupo").asText()).isEqualTo("Tamanho");
        assertThat(it.get("opcoes").get(0).get("nome").asText()).isEqualTo("Grande");

        // Mudar ou remover a opção depois não reescreve o pedido.
        api.chamar(c.s(), put("/api/produtos/" + c.pizzaId() + "/opcoes"), """
                {"grupos":[{"id":%d,"nome":"Tamanho","minimo":1,"maximo":1,"opcoes":[{"id":%d,"nome":"Individual","preco":0}]}]}
                """.formatted(c.produto().get("grupos").get(0).get("id").asLong(), c.opcao("Tamanho", "Média"))).andExpect(status().isOk());
        JsonNode depois = api.json(api.chamar(c.s(), get("/api/pedidos/" + id), null)).get("itens").get(0);
        assertThat(depois.get("opcoes").get(0).get("nome").asText()).isEqualTo("Grande");
        assertThat(depois.get("precoUnitario").decimalValue()).isEqualByComparingTo("80.90");

        // O relatório conta o que foi cobrado, com as opções.
        api.concluir(c.s(), id);
        JsonNode rel = api.json(api.chamar(c.s(), get("/api/relatorios/vendas?inicio=2026-09-23&fim=2026-09-23"), null));
        assertThat(rel.get("produtos").get(0).get("faturamento").decimalValue()).isEqualByComparingTo("161.80");
    }

    @Test
    void acompanhamentoMostraAsOpcoes() throws Exception {
        Cenario c = pizzaria("op-acomp@teste.com");
        JsonNode criado = api.json(pedirPublico(c.s(), "[" + item(c.pizzaId(), 1, c.opcao("Tamanho", "Média")) + "]").andExpect(status().isOk()));
        assertThat(criado.get("itens").get(0).get("opcoes").get(0).get("nome").asText()).isEqualTo("Média");
        assertThat(criado.get("total").decimalValue()).isEqualByComparingTo("49.90");
    }

    @Test
    void regrasDeEscolha() throws Exception {
        Cenario c = pizzaria("op-regras@teste.com");
        pedirPublico(c.s(), "[" + item(c.pizzaId(), 1) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Escolha tamanho em \"Margherita\"."));
        pedirPublico(c.s(), "[" + item(c.pizzaId(), 1, c.opcao("Tamanho", "Média"), c.opcao("Tamanho", "Grande")) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Escolha no máximo 1 em Tamanho (\"Margherita\")."));
        pedirPublico(c.s(), "[" + item(c.pizzaId(), 1, c.opcao("Tamanho", "Média"), c.opcao("Tamanho", "Média")) + "]")
                .andExpect(status().isBadRequest());

        // Opção de outro produto não vale.
        long outro = api.criarProduto(c.s(), api.criarCategoria(c.s(), "Massas"), "Lasanha", "42.90");
        pedirPublico(c.s(), "[" + item(outro, 1, c.opcao("Tamanho", "Média")) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("As opções de \"Lasanha\" mudaram. Atualize a página e escolha de novo."));

        // Opção esgotada (a equipe marca no meio do serviço).
        api.chamar(c.s(), patch("/api/produtos/" + c.pizzaId() + "/opcoes/" + c.opcao("Borda", "Catupiry") + "/disponivel?valor=false"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos[1].opcoes[0].disponivel").value(false));
        pedirPublico(c.s(), "[" + item(c.pizzaId(), 1, c.opcao("Tamanho", "Média"), c.opcao("Borda", "Catupiry")) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("\"Catupiry\" esgotou em \"Margherita\". Escolha outra opção."));
    }

    @Test
    void meioAMeioCobraOSaborMaisCaro() throws Exception {
        Sessao s = api.registrar("op-meio@teste.com");
        api.abrirLoja(s, "0");
        long meio = api.criarProduto(s, api.criarCategoria(s, "Pizzas"), "Pizza meio a meio", "0");
        JsonNode p = api.json(api.chamar(s, put("/api/produtos/" + meio + "/opcoes"), """
                {"grupos":[{"nome":"Sabores","minimo":2,"maximo":2,"cobranca":"MAIOR",
                  "opcoes":[{"nome":"Calabresa","preco":46},{"nome":"Quatro queijos","preco":54.9},{"nome":"Margherita","preco":49.9}]}]}
                """).andExpect(status().isOk()));
        // "A partir de": os dois sabores mais baratos, cobrando o mais caro deles.
        assertThat(p.get("precoMinimo").decimalValue()).isEqualByComparingTo("49.90");

        JsonNode sabores = p.get("grupos").get(0).get("opcoes");
        long id = api.pedir(s, "11987650002", "RETIRADA", "PIX",
                "[" + item(meio, 1, sabores.get(0).get("id").asLong(), sabores.get(1).get("id").asLong()) + "]");
        JsonNode it = api.json(api.chamar(s, get("/api/pedidos/" + id), null)).get("itens").get(0);
        assertThat(it.get("precoUnitario").decimalValue()).isEqualByComparingTo("54.90");

        pedirPublico(s, "[" + item(meio, 1, sabores.get(0).get("id").asLong()) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Escolha pelo menos 2 em Sabores (\"Pizza meio a meio\")."));
    }

    @Test
    void salvarMantemIdsEValidaOCadastro() throws Exception {
        Cenario c = pizzaria("op-salvar@teste.com");
        long grupoTamanho = c.produto().get("grupos").get(0).get("id").asLong();
        long grande = c.opcao("Tamanho", "Grande");

        JsonNode p = api.json(api.chamar(c.s(), put("/api/produtos/" + c.pizzaId() + "/opcoes"), """
                {"grupos":[{"id":%d,"nome":"Tamanho","minimo":1,"maximo":1,
                  "opcoes":[{"id":%d,"nome":"Grande (8 fatias)","preco":15},{"nome":"Família","preco":25}]}]}
                """.formatted(grupoTamanho, grande)).andExpect(status().isOk()));
        assertThat(p.get("grupos")).hasSize(1);
        assertThat(p.get("grupos").get(0).get("id").asLong()).isEqualTo(grupoTamanho);
        assertThat(p.get("grupos").get(0).get("opcoes").get(0).get("id").asLong()).isEqualTo(grande);
        assertThat(p.get("grupos").get(0).get("opcoes").get(0).get("nome").asText()).isEqualTo("Grande (8 fatias)");
        assertThat(p.get("precoMinimo").decimalValue()).isEqualByComparingTo("64.90");

        api.chamar(c.s(), put("/api/produtos/" + c.pizzaId() + "/opcoes"), """
                {"grupos":[{"nome":"Adicionais","minimo":3,"maximo":2,"opcoes":[{"nome":"A","preco":1},{"nome":"B","preco":1},{"nome":"C","preco":1}]}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Em \"Adicionais\", o mínimo de escolhas não pode ser maior que o máximo."));
        api.chamar(c.s(), put("/api/produtos/" + c.pizzaId() + "/opcoes"), """
                {"grupos":[{"nome":"Borda","minimo":0,"maximo":1,"opcoes":[{"nome":"Catupiry","preco":-1}]}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O preço da opção não pode ser negativo."));

        // Outro restaurante não mexe nas opções deste.
        Sessao outro = api.registrar("op-salvar-outro@teste.com");
        api.chamar(outro, put("/api/produtos/" + c.pizzaId() + "/opcoes"), "{\"grupos\":[]}").andExpect(status().isNotFound());

        // Lista vazia remove todos os grupos.
        JsonNode vazio = api.json(api.chamar(c.s(), put("/api/produtos/" + c.pizzaId() + "/opcoes"), "{\"grupos\":[]}").andExpect(status().isOk()));
        assertThat(vazio.get("grupos")).isEmpty();
    }
}
