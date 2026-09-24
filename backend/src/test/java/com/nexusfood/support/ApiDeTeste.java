package com.nexusfood.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.plataforma.util.CpfTestFixture;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Atalhos para montar cenários pela API de verdade (cadastro, cardápio, pedidos). */
public class ApiDeTeste {

    public record Sessao(String token, String slug) {}

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiDeTeste(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    public Sessao registrar(String email) throws Exception {
        JsonNode json = json(mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeRestaurante":"Restaurante %s","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"123456"}
                                """.formatted(email, email, CpfTestFixture.gerar(email))))
                .andExpect(status().isOk()));
        return new Sessao(json.get("token").asText(), json.get("restauranteSlug").asText());
    }

    public ResultActions chamar(Sessao s, MockHttpServletRequestBuilder req, String corpo) throws Exception {
        req.header("Authorization", "Bearer " + s.token());
        if (corpo != null) req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        return mockMvc.perform(req);
    }

    /** Loja aberta, retirada + entrega com taxa fixa, sem pedido mínimo. */
    public void abrirLoja(Sessao s, String taxaEntrega) throws Exception {
        chamar(s, put("/api/restaurante"), """
                {"nome":"Restaurante","fusoHorario":"America/Sao_Paulo","horaViradaDia":"04:00",
                 "aceitaRetirada":true,"aceitaEntrega":true,"aceitaConsumoLocal":true,
                 "tipoTaxaEntrega":"FIXA","taxaEntregaFixa":%s,"pedidoMinimo":0,"tempoPreparoEstimadoMin":30}
                """.formatted(taxaEntrega)).andExpect(status().isOk());
        chamar(s, patch("/api/restaurante/aceitando-pedidos?valor=true"), null).andExpect(status().isOk());
    }

    public long criarCategoria(Sessao s, String nome) throws Exception {
        return json(chamar(s, post("/api/categorias"), "{\"nome\":\"%s\"}".formatted(nome)).andExpect(status().isOk())).get("id").asLong();
    }

    public long criarProduto(Sessao s, long categoriaId, String nome, String preco) throws Exception {
        return json(chamar(s, post("/api/produtos"), """
                {"categoriaId":%d,"nome":"%s","preco":%s}
                """.formatted(categoriaId, nome, preco)).andExpect(status().isOk())).get("id").asLong();
    }

    /** Faz um pedido pelo cardápio público e devolve o id interno (como o painel vê). */
    public long pedir(Sessao s, String telefone, String modalidade, String pagamento, String itensJson) throws Exception {
        String endereco = "ENTREGA".equals(modalidade) ? ",\"enderecoEntrega\":\"Rua A, 1\"" : "";
        JsonNode criado = json(mockMvc.perform(post("/public/restaurantes/{slug}/pedidos", s.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chaveIdempotencia":"%s","nomeCliente":"Cliente %s","telefoneCliente":"%s",
                                 "modalidade":"%s","formaPagamento":"%s","itens":%s %s}
                                """.formatted(UUID.randomUUID(), telefone, telefone, modalidade, pagamento, itensJson, endereco)))
                .andExpect(status().isOk()));
        String codigo = criado.get("codigoPublico").asText();
        for (JsonNode p : json(chamar(s, get("/api/pedidos/em-andamento"), null))) {
            if (p.get("codigoPublico").asText().equals(codigo)) return p.get("id").asLong();
        }
        throw new IllegalStateException("Pedido recém-criado não apareceu no painel");
    }

    public void concluir(Sessao s, long pedidoId) throws Exception {
        chamar(s, patch("/api/pedidos/" + pedidoId + "/status"), "{\"status\":\"CONCLUIDO\"}").andExpect(status().isOk());
    }

    public void cancelar(Sessao s, long pedidoId, String motivo) throws Exception {
        chamar(s, patch("/api/pedidos/" + pedidoId + "/status"),
                "{\"status\":\"CANCELADO\",\"motivoCancelamento\":\"%s\"}".formatted(motivo)).andExpect(status().isOk());
    }
}
