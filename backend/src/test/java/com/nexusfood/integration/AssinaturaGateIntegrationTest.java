package com.nexusfood.integration;

import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que um restaurante com o trial vencido fica de fato barrado do painel — e que ele
 * nunca fica presa sem conseguir ver a própria tela de assinatura para resolver a situação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AssinaturaGateIntegrationTest {

    private record Sessao(String token, Long restauranteId, String slug) {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestauranteRepository restauranteRepository;

    private Sessao registrar(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeRestaurante":"Restaurante Teste","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"123456"}
                                """.formatted(email, com.nexusfood.plataforma.util.CpfTestFixture.gerar(email))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return new Sessao(json.get("token").asText(), json.get("restauranteId").asLong(), json.get("restauranteSlug").asText());
    }

    private void expirarTrial(Long restauranteId, int diasNoPassado) {
        Restaurante restaurante = restauranteRepository.findById(restauranteId).orElseThrow();
        restaurante.setDataFimTrial(LocalDate.now().minusDays(diasNoPassado));
        restauranteRepository.save(restaurante);
    }

    @Test
    void trialAtivoLiberaAcessoAoPainel() throws Exception {
        Sessao sessao = registrar("trial-ativo@teste.com");

        mockMvc.perform(get("/api/clientes").header("Authorization", "Bearer " + sessao.token()))
                .andExpect(status().isOk());
    }

    @Test
    void trialExpiradoBloqueiaPainelComStatus402() throws Exception {
        Sessao sessao = registrar("trial-expirado@teste.com");
        expirarTrial(sessao.restauranteId(), 1);

        mockMvc.perform(get("/api/clientes").header("Authorization", "Bearer " + sessao.token()))
                .andExpect(status().is(402));
    }

    @Test
    void telaDeAssinaturaNuncaFicaBloqueadaMesmoComTrialExpirado() throws Exception {
        Sessao sessao = registrar("consegue-ver-assinatura@teste.com");
        expirarTrial(sessao.restauranteId(), 30);

        mockMvc.perform(get("/api/assinatura").header("Authorization", "Bearer " + sessao.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acessoLiberado").value(false));
    }

    @Test
    void statusAtivaLiberaAcessoMesmoDepoisDoTrialExpirar() throws Exception {
        Sessao sessao = registrar("pagou@teste.com");
        Restaurante restaurante = restauranteRepository.findById(sessao.restauranteId()).orElseThrow();
        restaurante.setDataFimTrial(LocalDate.now().minusDays(10));
        restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
        restauranteRepository.save(restaurante);

        mockMvc.perform(get("/api/clientes").header("Authorization", "Bearer " + sessao.token()))
                .andExpect(status().isOk());
    }

    @Test
    void cardapioPublicoNaoEBloqueadoPeloTrialDoRestaurante() throws Exception {
        Sessao sessao = registrar("cardapio-publico@teste.com");
        expirarTrial(sessao.restauranteId(), 30);

        MvcResult resultado = mockMvc.perform(get("/public/restaurantes/{slug}", sessao.slug()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resposta = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(resposta.get("nome").asText()).isEqualTo("Restaurante Teste");
    }
}
