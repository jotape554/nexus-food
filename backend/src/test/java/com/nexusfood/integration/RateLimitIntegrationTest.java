package com.nexusfood.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O cardápio público não pode ser usado para inundar o painel de um restaurante com pedidos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.rate-limit.pedidos-por-minuto=2")
class RateLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void mesmoIpPassandoDoLimiteRecebe429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/public/restaurantes/qualquer/pedidos")
                            .header("X-Forwarded-For", "203.0.113.7")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/public/restaurantes/qualquer/pedidos")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(429));

        mockMvc.perform(post("/public/restaurantes/qualquer/pedidos")
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
