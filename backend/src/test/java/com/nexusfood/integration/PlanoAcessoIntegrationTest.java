package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.support.ApiDeTeste;
import com.nexusfood.support.ApiDeTeste.Sessao;
import com.nexusfood.support.RelogioDeTesteConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz plano × rota: o que cada plano abre, o que devolve 402 com o plano que resolve, e os
 * limites dentro de uma rota liberada (histórico do relatório, detalhes do Nexus).
 * "Hoje" nos testes é 23/09/2026 (RelogioDeTesteConfig).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class PlanoAcessoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestauranteRepository restauranteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
    }

    private static final String[] ROTAS = {
            "/api/pedidos/em-andamento", "/api/produtos", "/api/categorias", "/api/clientes",
            "/api/relatorios/vendas", "/api/nexus", "/api/usuarios", "/api/restaurante", "/api/assinatura"
    };

    private Sessao restauranteNoPlano(String email, PlanoSaas plano) throws Exception {
        Sessao s = api.registrar(email);
        if (plano != null) {
            var restaurante = restauranteRepository.findById(
                    usuarioRepository.findByEmail(email).orElseThrow().getRestaurante().getId()).orElseThrow();
            restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
            restaurante.setPlanoSaas(plano);
            restauranteRepository.save(restaurante);
        }
        return s;
    }

    private Map<String, Integer> statusDasRotas(Sessao s) throws Exception {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (String rota : ROTAS) {
            resultado.put(rota, api.chamar(s, get(rota), null).andReturn().getResponse().getStatus());
        }
        return resultado;
    }

    @Test
    void matrizPlanoPorRota() throws Exception {
        Map<String, Integer> basico = statusDasRotas(restauranteNoPlano("matriz-basico@teste.com", PlanoSaas.BASICO));
        Map<String, Integer> profissional = statusDasRotas(restauranteNoPlano("matriz-prof@teste.com", PlanoSaas.PROFISSIONAL));
        Map<String, Integer> premium = statusDasRotas(restauranteNoPlano("matriz-premium@teste.com", PlanoSaas.PREMIUM));
        Map<String, Integer> teste = statusDasRotas(restauranteNoPlano("matriz-trial@teste.com", null));

        assertThat(basico).containsEntry("/api/nexus", 402);
        basico.forEach((rota, st) -> { if (!rota.equals("/api/nexus")) assertThat(st).as("Básico " + rota).isEqualTo(200); });
        profissional.forEach((rota, st) -> assertThat(st).as("Profissional " + rota).isEqualTo(200));
        premium.forEach((rota, st) -> assertThat(st).as("Premium " + rota).isEqualTo(200));
        teste.forEach((rota, st) -> assertThat(st).as("Teste grátis " + rota).isEqualTo(200));
    }

    @Test
    void recursoForaDoPlanoResponde402ComOPlanoQueResolve() throws Exception {
        Sessao s = restauranteNoPlano("nexus-basico@teste.com", PlanoSaas.BASICO);
        api.chamar(s, get("/api/nexus"), null)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.upgradeNecessario").value(true))
                .andExpect(jsonPath("$.recurso").value("NEXUS_SCORE"))
                .andExpect(jsonPath("$.planoNecessario").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.mensagem").value("Nexus Score faz parte do plano Profissional ou superior."));
    }

    @Test
    void profissionalVeANotaMasNaoOsDetalhes() throws Exception {
        Sessao s = restauranteNoPlano("nexus-prof@teste.com", PlanoSaas.PROFISSIONAL);
        JsonNode r = api.json(api.chamar(s, get("/api/nexus"), null).andExpect(status().isOk()));
        assertThat(r.get("detalhesLiberados").asBoolean()).isFalse();
        assertThat(r.get("insights")).isEmpty();
        JsonNode indicador = r.get("areas").get(0).get("indicadores").get(0);
        assertThat(indicador.get("nome").asText()).isNotBlank();
        assertThat(indicador.get("valor").isNull()).isTrue();
        assertThat(indicador.get("pontos").isNull()).isTrue();
        assertThat(indicador.get("status").asText()).isEqualTo("BLOQUEADO");

        Sessao premium = restauranteNoPlano("nexus-premium@teste.com", PlanoSaas.PREMIUM);
        JsonNode completo = api.json(api.chamar(premium, get("/api/nexus"), null).andExpect(status().isOk()));
        assertThat(completo.get("detalhesLiberados").asBoolean()).isTrue();
        assertThat(completo.get("areas").get(0).get("indicadores").get(0).get("status").asText()).isNotEqualTo("BLOQUEADO");
    }

    @Test
    void historicoDoRelatorioRespeitaOPlano() throws Exception {
        Sessao basico = restauranteNoPlano("hist-basico@teste.com", PlanoSaas.BASICO);
        // Padrão (últimos 30 dias) sempre abre, e a resposta diz até onde o plano vai.
        api.chamar(basico, get("/api/relatorios/vendas"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primeiroDiaPermitido").value("2026-08-25"));
        api.chamar(basico, get("/api/relatorios/vendas?inicio=2026-09-01&fim=2026-09-23"), null).andExpect(status().isOk());
        api.chamar(basico, get("/api/relatorios/vendas?inicio=2026-08-01&fim=2026-08-31"), null)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.planoNecessario").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.primeiroDiaPermitido").value("2026-08-25"))
                .andExpect(jsonPath("$.mensagem").value("Seu plano mostra relatórios a partir de 25/08/2026. Períodos mais antigos fazem parte do plano Profissional."));

        Sessao profissional = restauranteNoPlano("hist-prof@teste.com", PlanoSaas.PROFISSIONAL);
        api.chamar(profissional, get("/api/relatorios/vendas?inicio=2025-10-01&fim=2026-09-23&agrupamento=MES"), null).andExpect(status().isOk());
        api.chamar(profissional, get("/api/relatorios/vendas?inicio=2025-06-01&fim=2025-06-30"), null)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.planoNecessario").value("PREMIUM"));

        Sessao premium = restauranteNoPlano("hist-premium@teste.com", PlanoSaas.PREMIUM);
        api.chamar(premium, get("/api/relatorios/vendas?inicio=2024-01-01&fim=2024-12-31&agrupamento=MES"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primeiroDiaPermitido").isEmpty());
    }

    @Test
    void assinaturaDizOQueEstaLiberado() throws Exception {
        Sessao s = restauranteNoPlano("status-prof@teste.com", PlanoSaas.PROFISSIONAL);
        JsonNode st = api.json(api.chamar(s, get("/api/assinatura"), null).andExpect(status().isOk()));
        assertThat(st.get("planoEfetivo").asText()).isEqualTo("PROFISSIONAL");
        assertThat(st.get("recursos").get("NEXUS_SCORE").asBoolean()).isTrue();
        assertThat(st.get("recursos").get("NEXUS_DETALHES").asBoolean()).isFalse();
        assertThat(st.get("limiteUsuarios").asInt()).isEqualTo(5);
        assertThat(st.get("usuariosAtivos").asInt()).isEqualTo(1);
        assertThat(st.get("primeiroDiaRelatorio").asText()).isEqualTo("2025-09-24");

        Sessao trial = restauranteNoPlano("status-trial@teste.com", null);
        JsonNode t = api.json(api.chamar(trial, get("/api/assinatura"), null));
        assertThat(t.get("status").asText()).isEqualTo("TRIAL");
        assertThat(t.get("planoEfetivo").asText()).isEqualTo("PREMIUM");
        assertThat(t.get("limiteUsuarios").isNull()).isTrue();

        JsonNode planos = api.json(api.chamar(trial, get("/api/assinatura/planos"), null));
        assertThat(planos).hasSize(3);
        assertThat(planos.get(0).get("recursos").toString()).doesNotContain("NEXUS");
        assertThat(planos.get(1).get("recursos").toString()).contains("NEXUS_SCORE").doesNotContain("NEXUS_DETALHES");
        assertThat(planos.get(2).get("limiteUsuarios").isNull()).isTrue();
    }
}
