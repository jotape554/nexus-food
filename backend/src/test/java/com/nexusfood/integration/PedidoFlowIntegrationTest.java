package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.clientes.repository.ClienteRepository;
import com.nexusfood.plataforma.util.CpfTestFixture;
import com.nexusfood.support.RelogioDeTeste;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de ponta a ponta da Fase 2: o restaurante monta o cardápio e abre a loja, o cliente
 * pede pelo cardápio público e a equipe move o pedido no painel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class PedidoFlowIntegrationTest {

    private record Sessao(String token, String slug) {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RelogioDeTeste relogio;
    @Autowired private ClienteRepository clienteRepository;

    @BeforeEach
    void resetarRelogio() {
        relogio.definir(RelogioDeTesteConfig.INICIO);
    }

    // ---------- helpers ----------

    private Sessao registrar(String email) throws Exception {
        JsonNode json = json(mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeRestaurante":"Pizzaria %s","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"123456"}
                                """.formatted(email, email, CpfTestFixture.gerar(email))))
                .andExpect(status().isOk()));
        return new Sessao(json.get("token").asText(), json.get("restauranteSlug").asText());
    }

    private ResultActions api(Sessao s, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String corpo) throws Exception {
        req.header("Authorization", "Bearer " + s.token());
        if (corpo != null) req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mockMvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private void configurar(Sessao s, String extra) throws Exception {
        api(s, put("/api/restaurante"), """
                {"nome":"Pizzaria","fusoHorario":"America/Sao_Paulo","horaViradaDia":"04:00",
                 "aceitaRetirada":true,"aceitaEntrega":true,"aceitaConsumoLocal":false,
                 "tipoTaxaEntrega":"FIXA","taxaEntregaFixa":7.00,"pedidoMinimo":20.00,"tempoPreparoEstimadoMin":40 %s}
                """.formatted(extra == null ? "" : extra)).andExpect(status().isOk());
        api(s, patch("/api/restaurante/aceitando-pedidos?valor=true"), null).andExpect(status().isOk());
    }

    private long criarProduto(Sessao s, String nome, String preco) throws Exception {
        long categoriaId = json(api(s, post("/api/categorias"), "{\"nome\":\"Pizzas\"}").andExpect(status().isOk())).get("id").asLong();
        return json(api(s, post("/api/produtos"), """
                {"categoriaId":%d,"nome":"%s","preco":%s}
                """.formatted(categoriaId, nome, preco)).andExpect(status().isOk())).get("id").asLong();
    }

    private ResultActions pedir(String slug, String chave, String telefone, String modalidade, String itensJson, String extra) throws Exception {
        return mockMvc.perform(post("/public/restaurantes/{slug}/pedidos", slug)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"chaveIdempotencia":"%s","nomeCliente":"Maria","telefoneCliente":"%s",
                         "modalidade":"%s","formaPagamento":"PIX","itens":%s %s}
                        """.formatted(chave, telefone, modalidade, itensJson, extra == null ? "" : extra)));
    }

    private static String item(long produtoId, int qtd) {
        return "[{\"produtoId\":%d,\"quantidade\":%d}]".formatted(produtoId, qtd);
    }

    // ---------- testes ----------

    @Test
    void cardapioPublicoMostraSoOQueEstaAtivo() throws Exception {
        Sessao s = registrar("cardapio@teste.com");
        long produto = criarProduto(s, "Margherita", "45.00");
        long removido = criarProduto(s, "Calabresa", "40.00");
        api(s, delete("/api/produtos/" + removido), null).andExpect(status().isNoContent());

        mockMvc.perform(get("/public/restaurantes/{slug}/cardapio", s.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias", hasSize(1)))
                .andExpect(jsonPath("$.categorias[0].produtos", hasSize(1)))
                .andExpect(jsonPath("$.categorias[0].produtos[0].id").value(produto));

        mockMvc.perform(get("/public/restaurantes/{slug}", s.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planoSaas").doesNotExist())
                .andExpect(jsonPath("$.stripeCustomerId").doesNotExist());
    }

    @Test
    void precoTotalENumeroDoDiaSaoCalculadosNoServidor() throws Exception {
        Sessao s = registrar("preco@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");

        JsonNode p1 = json(pedir(s.slug(), UUID.randomUUID().toString(), "(11) 98888-7777", "ENTREGA", item(produto, 2),
                ",\"enderecoEntrega\":\"Rua A, 10\"").andExpect(status().isOk()));
        assertThat(p1.get("subtotal").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(p1.get("taxaEntrega").decimalValue()).isEqualByComparingTo("7.00");
        assertThat(p1.get("total").decimalValue()).isEqualByComparingTo("97.00");
        assertThat(p1.get("numeroDia").asInt()).isEqualTo(1);
        assertThat(p1.get("status").asText()).isEqualTo("RECEBIDO");
        assertThat(Instant.parse(p1.get("prontoPrevistoPara").asText()))
                .isEqualTo(RelogioDeTesteConfig.INICIO.plus(Duration.ofMinutes(40)));

        // preço muda depois: o pedido antigo continua com o valor da venda
        api(s, put("/api/produtos/" + produto), """
                {"categoriaId":%d,"nome":"Margherita","preco":60.00}
                """.formatted(json(api(s, get("/api/produtos"), null)).get(0).get("categoriaId").asLong()))
                .andExpect(status().isOk());

        JsonNode p2 = json(pedir(s.slug(), UUID.randomUUID().toString(), "11988887777", "RETIRADA", item(produto, 1), null)
                .andExpect(status().isOk()));
        assertThat(p2.get("numeroDia").asInt()).isEqualTo(2);
        assertThat(p2.get("total").decimalValue()).isEqualByComparingTo("60.00");

        mockMvc.perform(get("/public/pedidos/{c}", p1.get("codigoPublico").asText()))
                .andExpect(jsonPath("$.total").value(97.00))
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.cliente").doesNotExist());
    }

    @Test
    void mesmoTelefoneEmFormatosDiferentesEOMesmoCliente() throws Exception {
        Sessao s = registrar("telefone@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");

        pedir(s.slug(), UUID.randomUUID().toString(), "(11) 97777-6666", "RETIRADA", item(produto, 1), null).andExpect(status().isOk());
        pedir(s.slug(), UUID.randomUUID().toString(), "+55 11 97777-6666", "RETIRADA", item(produto, 1), null).andExpect(status().isOk());

        JsonNode pedidos = json(api(s, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk()));
        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).get("cliente").get("id").asLong()).isEqualTo(pedidos.get(1).get("cliente").get("id").asLong());
        assertThat(pedidos.get(0).get("cliente").get("telefone").asText()).isEqualTo("5511977776666");
    }

    @Test
    void reenviarOMesmoPedidoNaoDuplica() throws Exception {
        Sessao s = registrar("idempotencia@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");
        String chave = UUID.randomUUID().toString();

        JsonNode a = json(pedir(s.slug(), chave, "11966665555", "RETIRADA", item(produto, 1), null).andExpect(status().isOk()));
        JsonNode b = json(pedir(s.slug(), chave, "11966665555", "RETIRADA", item(produto, 1), null).andExpect(status().isOk()));

        assertThat(b.get("codigoPublico").asText()).isEqualTo(a.get("codigoPublico").asText());
        assertThat(json(api(s, get("/api/pedidos"), null))).hasSize(1);
    }

    @Test
    void regrasDoRestauranteSaoRespeitadas() throws Exception {
        Sessao s = registrar("regras@teste.com");
        long produto = criarProduto(s, "Margherita", "15.00");

        // loja fechada
        pedir(s.slug(), UUID.randomUUID().toString(), "11955554444", "RETIRADA", item(produto, 2), null)
                .andExpect(status().isBadRequest());

        configurar(s, null);
        // abaixo do pedido mínimo (20,00)
        pedir(s.slug(), UUID.randomUUID().toString(), "11955554444", "RETIRADA", item(produto, 1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("mínimo")));
        // modalidade não aceita
        pedir(s.slug(), UUID.randomUUID().toString(), "11955554444", "CONSUMO_LOCAL", item(produto, 2), null)
                .andExpect(status().isBadRequest());
        // entrega sem endereço
        pedir(s.slug(), UUID.randomUUID().toString(), "11955554444", "ENTREGA", item(produto, 2), null)
                .andExpect(status().isBadRequest());
        // telefone inválido
        pedir(s.slug(), UUID.randomUUID().toString(), "1234", "RETIRADA", item(produto, 2), null)
                .andExpect(status().isBadRequest());
        // produto esgotado
        api(s, patch("/api/produtos/" + produto + "/disponivel?valor=false"), null).andExpect(status().isOk());
        pedir(s.slug(), UUID.randomUUID().toString(), "11955554444", "RETIRADA", item(produto, 2), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("esgotou")));
    }

    @Test
    void taxaPorBairroUsaOBairroEscolhido() throws Exception {
        Sessao s = registrar("bairro@teste.com");
        configurar(s, null);
        api(s, put("/api/restaurante"), """
                {"nome":"Pizzaria","fusoHorario":"America/Sao_Paulo","horaViradaDia":"04:00",
                 "aceitaRetirada":true,"aceitaEntrega":true,"aceitaConsumoLocal":false,
                 "tipoTaxaEntrega":"POR_BAIRRO","taxaEntregaFixa":0,"pedidoMinimo":0,"tempoPreparoEstimadoMin":30}
                """).andExpect(status().isOk());
        long centro = json(api(s, post("/api/restaurante/bairros"), "{\"nome\":\"Centro\",\"taxa\":5.50}")
                .andExpect(status().isOk())).get("id").asLong();
        long produto = criarProduto(s, "Margherita", "45.00");

        pedir(s.slug(), UUID.randomUUID().toString(), "11944443333", "ENTREGA", item(produto, 1),
                ",\"enderecoEntrega\":\"Rua B, 5\"").andExpect(status().isBadRequest());

        JsonNode p = json(pedir(s.slug(), UUID.randomUUID().toString(), "11944443333", "ENTREGA", item(produto, 1),
                ",\"enderecoEntrega\":\"Rua B, 5\",\"bairroId\":%d".formatted(centro)).andExpect(status().isOk()));
        assertThat(p.get("taxaEntrega").decimalValue()).isEqualByComparingTo("5.50");
        assertThat(p.get("total").decimalValue()).isEqualByComparingTo("50.50");

        mockMvc.perform(get("/public/restaurantes/{slug}", s.slug()))
                .andExpect(jsonPath("$.bairros", hasSize(1)))
                .andExpect(jsonPath("$.bairros[0].nome").value("Centro"));
    }

    @Test
    void painelMoveOPedidoERegistraCadaMudanca() throws Exception {
        Sessao s = registrar("painel@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");
        JsonNode criado = json(pedir(s.slug(), UUID.randomUUID().toString(), "11933332222", "RETIRADA", item(produto, 1), null)
                .andExpect(status().isOk()));
        long id = json(api(s, get("/api/pedidos/em-andamento"), null)).get(0).get("id").asLong();

        relogio.avancar(Duration.ofMinutes(2));
        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"CONFIRMADO\"}").andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(20));
        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"PRONTO\"}").andExpect(status().isOk());
        // retirada não tem "saiu para entrega"
        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"SAIU_PARA_ENTREGA\"}").andExpect(status().isBadRequest());
        // não volta para trás
        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"CONFIRMADO\"}").andExpect(status().isBadRequest());
        relogio.avancar(Duration.ofMinutes(5));
        JsonNode concluido = json(api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"CONCLUIDO\"}").andExpect(status().isOk()));

        assertThat(Instant.parse(concluido.get("confirmadoEm").asText())).isEqualTo(RelogioDeTesteConfig.INICIO.plus(Duration.ofMinutes(2)));
        assertThat(Instant.parse(concluido.get("prontoEm").asText())).isEqualTo(RelogioDeTesteConfig.INICIO.plus(Duration.ofMinutes(22)));
        assertThat(concluido.get("eventos")).hasSize(4);
        assertThat(concluido.get("eventos").get(1).get("usuario").asText()).isEqualTo("Admin");

        assertThat(json(api(s, get("/api/pedidos/em-andamento"), null))).isEmpty();
        mockMvc.perform(get("/public/pedidos/{c}", criado.get("codigoPublico").asText()))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    void cancelarExigeMotivo() throws Exception {
        Sessao s = registrar("cancelar@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");
        pedir(s.slug(), UUID.randomUUID().toString(), "11922221111", "RETIRADA", item(produto, 1), null).andExpect(status().isOk());
        long id = json(api(s, get("/api/pedidos/em-andamento"), null)).get(0).get("id").asLong();

        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"CANCELADO\"}").andExpect(status().isBadRequest());
        api(s, patch("/api/pedidos/" + id + "/status"), "{\"status\":\"CANCELADO\",\"motivoCancelamento\":\"ITEM_INDISPONIVEL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladoPor").value("RESTAURANTE"));
    }

    @Test
    void pedidoDeMadrugadaEntraNoDiaOperacionalAnterior() throws Exception {
        Sessao s = registrar("madrugada@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");

        relogio.definir(Instant.parse("2026-09-26T03:40:00Z")); // sábado 00:40 em São Paulo
        pedir(s.slug(), UUID.randomUUID().toString(), "11911110000", "RETIRADA", item(produto, 1), null).andExpect(status().isOk());

        JsonNode sexta = json(api(s, get("/api/pedidos?dia=2026-09-25"), null).andExpect(status().isOk()));
        assertThat(sexta).hasSize(1);
        assertThat(sexta.get(0).get("diaOperacional").asText()).isEqualTo("2026-09-25");
        assertThat(json(api(s, get("/api/pedidos?dia=2026-09-26"), null))).isEmpty();
    }

    @Test
    void umRestauranteNaoEnxergaNemMexeNosPedidosDoOutro() throws Exception {
        Sessao a = registrar("tenant-a@teste.com");
        Sessao b = registrar("tenant-b@teste.com");
        configurar(a, null);
        configurar(b, null);
        long produtoA = criarProduto(a, "Margherita", "45.00");

        pedir(a.slug(), UUID.randomUUID().toString(), "11900001111", "RETIRADA", item(produtoA, 1), null).andExpect(status().isOk());
        long idA = json(api(a, get("/api/pedidos/em-andamento"), null)).get(0).get("id").asLong();

        assertThat(json(api(b, get("/api/pedidos/em-andamento"), null))).isEmpty();
        api(b, get("/api/pedidos/" + idA), null).andExpect(status().isNotFound());
        api(b, patch("/api/pedidos/" + idA + "/status"), "{\"status\":\"CONFIRMADO\"}").andExpect(status().isNotFound());
        api(b, put("/api/produtos/" + produtoA), "{\"categoriaId\":1,\"nome\":\"X\",\"preco\":1}").andExpect(status().isNotFound());

        // produto de A não pode ser pedido no cardápio de B
        pedir(b.slug(), UUID.randomUUID().toString(), "11900001111", "RETIRADA", item(produtoA, 1), null)
                .andExpect(status().isBadRequest());

        // clientes com o mesmo telefone são independentes por restaurante
        assertThat(clienteRepository.findAll().stream().filter(c -> c.getTelefone().equals("5511900001111")).count()).isEqualTo(1);
    }

    @Test
    void listaEBuscaDeClientes() throws Exception {
        Sessao s = registrar("clientes@teste.com");
        configurar(s, null);
        long produto = criarProduto(s, "Margherita", "45.00");
        pedir(s.slug(), UUID.randomUUID().toString(), "(11) 91234-5678", "RETIRADA", item(produto, 1), null).andExpect(status().isOk());

        api(s, get("/api/clientes"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nome").value("Maria"));
        api(s, get("/api/clientes?busca=mar"), null).andExpect(jsonPath("$.content", hasSize(1)));
        api(s, get("/api/clientes?busca=(11) 91234"), null).andExpect(jsonPath("$.content", hasSize(1)));
        api(s, get("/api/clientes?busca=joao"), null).andExpect(jsonPath("$.content", hasSize(0)));
    }
}
