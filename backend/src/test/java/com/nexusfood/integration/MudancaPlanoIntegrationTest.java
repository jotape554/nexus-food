package com.nexusfood.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.plataforma.service.StripeGateway;
import com.nexusfood.support.ApiDeTeste;
import com.nexusfood.support.ApiDeTeste.Sessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escolher e trocar de plano. Sem assinatura: checkout. Com assinatura: troca o preço da
 * assinatura existente (nunca uma segunda cobrança). A Stripe é um dublê: nada sai para a rede.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MudancaPlanoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestauranteRepository restauranteRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @MockBean private StripeGateway stripe;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
    }

    private Restaurante restaurante(String email) {
        return restauranteRepository.findById(usuarioRepository.findByEmail(email).orElseThrow().getRestaurante().getId()).orElseThrow();
    }

    private void assinando(String email, PlanoSaas plano, String subscriptionId) {
        Restaurante r = restaurante(email);
        r.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
        r.setPlanoSaas(plano);
        r.setStripeCustomerId("cus_" + subscriptionId);
        r.setStripeSubscriptionId(subscriptionId);
        restauranteRepository.save(r);
    }

    @Test
    void semAssinaturaVaiParaOCheckout() throws Exception {
        Sessao s = api.registrar("plano-checkout@teste.com");
        when(stripe.criarCliente(any())).thenReturn("cus_novo");
        when(stripe.criarCheckout(any(), eq("cus_novo"), eq(PlanoSaas.PROFISSIONAL), eq("price_teste_profissional")))
                .thenReturn("https://checkout.stripe.com/c/pay/teste");

        api.chamar(s, post("/api/assinatura/plano?plano=PROFISSIONAL"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/teste"));
        verify(stripe, never()).trocarPreco(anyString(), anyString());
        // Só o webhook ativa: escolher o plano não muda nada sozinho.
        assertThat(restaurante("plano-checkout@teste.com").getStatusAssinaturaSaas()).isEqualTo(StatusAssinaturaSaas.TRIAL);
    }

    @Test
    void comAssinaturaTrocaOPrecoDaMesmaAssinatura() throws Exception {
        Sessao s = api.registrar("plano-troca@teste.com");
        assinando("plano-troca@teste.com", PlanoSaas.BASICO, "sub_troca");
        when(stripe.trocarPreco("sub_troca", "price_teste_premium")).thenReturn("price_teste_premium");

        api.chamar(s, post("/api/assinatura/plano?plano=PREMIUM"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isEmpty())
                .andExpect(jsonPath("$.plano").value("PREMIUM"));
        verify(stripe, never()).criarCheckout(any(), anyString(), any(), anyString());
        assertThat(restaurante("plano-troca@teste.com").getPlanoSaas()).isEqualTo(PlanoSaas.PREMIUM);
        api.chamar(s, get("/api/nexus"), null).andExpect(status().isOk());

        api.chamar(s, post("/api/assinatura/plano?plano=PREMIUM"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Este já é o seu plano."));
    }

    @Test
    void soOAdministradorEscolheOPlano() throws Exception {
        Sessao dono = api.registrar("plano-perfil@teste.com");
        JsonNode convite = api.json(api.chamar(dono, post("/api/usuarios"),
                "{\"nome\":\"Gerente\",\"email\":\"plano-perfil-g@teste.com\",\"papel\":\"GERENTE\"}"));
        String link = convite.get("link").asText();
        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"novaSenha\":\"senha-gerente\"}".formatted(link.substring(link.indexOf("token=") + 6))));
        String token = api.json(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"plano-perfil-g@teste.com\",\"senha\":\"senha-gerente\"}"))).get("token").asText();

        api.chamar(new Sessao(token, dono.slug()), post("/api/assinatura/plano?plano=PREMIUM"), null).andExpect(status().isForbidden());
    }

    @Test
    void mudancaFeitaNoPortalChegaPeloWebhook() throws Exception {
        Sessao s = api.registrar("plano-webhook@teste.com");
        assinando("plano-webhook@teste.com", PlanoSaas.PREMIUM, "sub_portal");

        enviarEvento("customer.subscription.updated", "sub_portal", "price_teste_basico", "active");
        assertThat(restaurante("plano-webhook@teste.com").getPlanoSaas()).isEqualTo(PlanoSaas.BASICO);
        api.chamar(s, get("/api/nexus"), null).andExpect(status().isPaymentRequired());

        // Atraso de pagamento mantém o acesso enquanto a Stripe tenta de novo; "unpaid" encerra.
        enviarEvento("customer.subscription.updated", "sub_portal", "price_teste_basico", "past_due");
        assertThat(restaurante("plano-webhook@teste.com").getStatusAssinaturaSaas()).isEqualTo(StatusAssinaturaSaas.ATIVA);
        enviarEvento("customer.subscription.updated", "sub_portal", "price_teste_basico", "unpaid");
        assertThat(restaurante("plano-webhook@teste.com").getStatusAssinaturaSaas()).isEqualTo(StatusAssinaturaSaas.CANCELADA);
    }

    private void enviarEvento(String tipo, String subscriptionId, String priceId, String statusStripe) throws Exception {
        String payload = """
                {"id":"evt_%s","object":"event","type":"%s","data":{"object":{
                  "id":"%s","object":"subscription","status":"%s",
                  "items":{"object":"list","data":[{"id":"si_1","object":"subscription_item","price":{"id":"%s","object":"price"}}]}
                }}}
                """.formatted(statusStripe, tipo, subscriptionId, statusStripe, priceId);
        long agora = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal((agora + "." + payload).getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "t=" + agora + ",v1=" + hex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }
}
