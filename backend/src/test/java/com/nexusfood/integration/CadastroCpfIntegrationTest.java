package com.nexusfood.integration;

import com.nexusfood.plataforma.util.CpfTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O CPF no cadastro existe pra impedir que a mesma pessoa fique reiniciando o período de
 * teste grátis criando contas com e-mails diferentes: precisa ser um CPF real (dígitos
 * verificadores corretos) e só pode ser usado uma vez.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CadastroCpfIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String corpoRegistro(String email, String cpf) {
        return """
                {"nomeRestaurante":"Restaurante Teste","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"123456"}
                """.formatted(email, cpf);
    }

    @Test
    void registroComCpfInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("cpf-invalido@teste.com", "111.111.111-11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Informe um CPF válido."));
    }

    @Test
    void registroComCpfDeFormatoErradoRetorna400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("cpf-curto@teste.com", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Informe um CPF válido."));
    }

    @Test
    void registroComCpfJaCadastradoEmOutraContaRetorna400() throws Exception {
        String cpf = CpfTestFixture.gerar("cpf-repetido");

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("dono-original@teste.com", cpf)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("segunda-conta-mesmo-cpf@teste.com", cpf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Já existe uma conta cadastrada com este CPF."));
    }

    @Test
    void registroComCpfValidoEFormatadoFuncionaNormalmente() throws Exception {
        String cpfFormatado = formatarComoUsuarioDigitaria(CpfTestFixture.gerar("cpf-formatado"));

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro("cpf-formatado@teste.com", cpfFormatado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    private String formatarComoUsuarioDigitaria(String cpfDigitos) {
        return cpfDigitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }
}
