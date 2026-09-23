package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A confirmação de pagamento só pode vir de um webhook com assinatura HMAC válida da Stripe —
 * nunca de uma chamada direta do navegador. Estes testes recriam a assinatura exatamente como
 * a Stripe faz (timestamp + payload, HMAC-SHA256 com o segredo do endpoint) para provar que o
 * controller aceita eventos legítimos e rejeita qualquer coisa forjada, sem depender da rede.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class StripeWebhookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private String assinar(String payload, long timestamp) throws Exception {
        String payloadAssinado = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payloadAssinado.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return "t=" + timestamp + ",v1=" + hex;
    }

    private Long registrarRestaurante(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeRestaurante":"Restaurante Teste","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"123456"}
                                """.formatted(email, com.nexusfood.plataforma.util.CpfTestFixture.gerar(email))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("restauranteId").asLong();
    }

    private String eventoCheckoutCompleto(Long restauranteId, String customerId, String subscriptionId) {
        return """
                {
                  "id": "evt_test",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test",
                      "object": "checkout.session",
                      "customer": "%s",
                      "subscription": "%s",
                      "metadata": { "restauranteId": "%d", "plano": "PROFISSIONAL" }
                    }
                  }
                }
                """.formatted(customerId, subscriptionId, restauranteId);
    }

    @Test
    void eventoComAssinaturaValidaAtivaAAssinatura() throws Exception {
        Long restauranteId = registrarRestaurante("webhook-valido@teste.com");
        String payload = eventoCheckoutCompleto(restauranteId, "cus_valido", "sub_valido");
        long agora = Instant.now().getEpochSecond();

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", assinar(payload, agora))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        String loginToken = login("webhook-valido@teste.com");
        MvcResult statusResultado = mockMvc.perform(get("/api/assinatura").header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode status = objectMapper.readTree(statusResultado.getResponse().getContentAsString());

        assertThat(status.get("status").asText()).isEqualTo("ATIVA");
        assertThat(status.get("plano").asText()).isEqualTo("PROFISSIONAL");
        assertThat(status.get("acessoLiberado").asBoolean()).isTrue();
    }

    @Test
    void eventoComAssinaturaForjadaEhRejeitadoENaoAtivaNada() throws Exception {
        Long restauranteId = registrarRestaurante("webhook-forjado@teste.com");
        String payload = eventoCheckoutCompleto(restauranteId, "cus_forjado", "sub_forjado");

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "t=1700000000,v1=assinaturacompletamenteinventada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        String loginToken = login("webhook-forjado@teste.com");
        MvcResult statusResultado = mockMvc.perform(get("/api/assinatura").header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode status = objectMapper.readTree(statusResultado.getResponse().getContentAsString());

        assertThat(status.get("status").asText()).isEqualTo("TRIAL");
    }

    private String login(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"123456"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
    }
}
