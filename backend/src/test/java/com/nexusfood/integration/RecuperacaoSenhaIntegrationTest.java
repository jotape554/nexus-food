package com.nexusfood.integration;

import com.nexusfood.plataforma.model.Usuario;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem servidor de e-mail de verdade configurado nos testes, o envio fica só simulado
 * (ver EmailService) — então aqui a gente pega o token direto do banco, exatamente como
 * ele ficaria disponível no link do e-mail em produção.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RecuperacaoSenhaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;

    private void registrar(String email) throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeRestaurante":"Restaurante Teste","nomeAdmin":"Admin","email":"%s","cpf":"%s","senha":"senhaAntiga123"}
                                """.formatted(email, com.nexusfood.plataforma.util.CpfTestFixture.gerar(email))))
                .andExpect(status().isOk());
    }

    private void login(String email, String senha, boolean deveFuncionar) throws Exception {
        var resultado = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)));
        resultado.andExpect(status().is(deveFuncionar ? 200 : 401));
    }

    @Test
    void redefineSenhaComTokenValidoEBloqueiaSenhaAntiga() throws Exception {
        String email = "esqueceu@teste.com";
        registrar(email);

        mockMvc.perform(post("/auth/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isNoContent());

        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(usuario.getResetSenhaToken()).isNotBlank();
        String token = usuario.getResetSenhaToken();

        mockMvc.perform(post("/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"senhaNova456\"}".formatted(token)))
                .andExpect(status().isNoContent());

        login(email, "senhaAntiga123", false);
        login(email, "senhaNova456", true);

        // o token não pode ser reaproveitado depois de usado
        mockMvc.perform(post("/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"outraSenha789\"}".formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenExpiradoNaoFunciona() throws Exception {
        String email = "expirou@teste.com";
        registrar(email);

        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setResetSenhaToken("token-vencido");
        usuario.setResetSenhaExpiraEm(Instant.now().minus(1, ChronoUnit.HOURS));
        usuarioRepository.save(usuario);

        mockMvc.perform(post("/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-vencido\",\"novaSenha\":\"novaSenha123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenInexistenteNaoFunciona() throws Exception {
        mockMvc.perform(post("/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nao-existe\",\"novaSenha\":\"novaSenha123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoParaEmailInexistenteNaoRevelaNadaERetornaOk() throws Exception {
        mockMvc.perform(post("/auth/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@teste.com\"}"))
                .andExpect(status().isNoContent());
    }
}
