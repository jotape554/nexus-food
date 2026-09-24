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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Equipe do restaurante: convite por link, perfis, limite de usuários do plano, desativação
 * com efeito imediato e as travas que impedem o restaurante de ficar sem administrador.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
class EquipeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestauranteRepository restauranteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private ApiDeTeste api;

    @BeforeEach
    void preparar() {
        api = new ApiDeTeste(mockMvc, objectMapper);
    }

    private void plano(String emailAdmin, PlanoSaas plano) {
        var restaurante = restauranteRepository.findById(
                usuarioRepository.findByEmail(emailAdmin).orElseThrow().getRestaurante().getId()).orElseThrow();
        restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
        restaurante.setPlanoSaas(plano);
        restauranteRepository.save(restaurante);
    }

    private ResultActions convidar(Sessao admin, String nome, String email, String papel) throws Exception {
        return api.chamar(admin, post("/api/usuarios"), """
                {"nome":"%s","email":"%s","papel":"%s"}
                """.formatted(nome, email, papel));
    }

    private static String token(JsonNode convite) {
        String link = convite.get("link").asText();
        return link.substring(link.indexOf("token=") + 6);
    }

    /** Convida, cria a senha pelo link e entra: devolve a sessão da pessoa convidada. */
    private Sessao entrarNaEquipe(Sessao admin, String nome, String email, String papel) throws Exception {
        JsonNode convite = api.json(convidar(admin, nome, email, papel).andExpect(status().isOk()));
        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"senha-forte-1\"}".formatted(token(convite))))
                .andExpect(status().isNoContent());
        return new Sessao(login(email, "senha-forte-1").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1"), admin.slug());
    }

    private ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)));
    }

    private long idDe(Sessao admin, String email) throws Exception {
        for (JsonNode u : api.json(api.chamar(admin, get("/api/usuarios"), null)).get("usuarios")) {
            if (u.get("email").asText().equals(email)) return u.get("id").asLong();
        }
        throw new IllegalStateException("usuário não encontrado: " + email);
    }

    @Test
    void conviteCriaUsuarioQueDefineAPropriaSenha() throws Exception {
        Sessao admin = api.registrar("eq-dono@teste.com");
        JsonNode convite = api.json(convidar(admin, "Carla Souza", "Carla.Souza@Teste.com", "ATENDENTE").andExpect(status().isOk()));
        assertThat(convite.get("link").asText()).startsWith("http://localhost:5173/redefinir-senha?token=");
        assertThat(convite.get("usuario").get("email").asText()).isEqualTo("carla.souza@teste.com");
        assertThat(convite.get("usuario").get("convitePendente").asBoolean()).isTrue();

        // Antes de criar a senha, ninguém entra com essa conta.
        login("carla.souza@teste.com", "qualquer").andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/convite").param("token", token(convite)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Carla Souza"))
                .andExpect(jsonPath("$.restaurante").value("Restaurante eq-dono@teste.com"))
                .andExpect(jsonPath("$.convite").value(true));

        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"senha-da-carla\"}".formatted(token(convite))))
                .andExpect(status().isNoContent());
        login("CARLA.souza@teste.com", "senha-da-carla").andExpect(status().isOk()).andExpect(jsonPath("$.papel").value("ATENDENTE"));

        JsonNode equipe = api.json(api.chamar(admin, get("/api/usuarios"), null));
        assertThat(equipe.get("usuariosAtivos").asInt()).isEqualTo(2);
        JsonNode carla = equipe.get("usuarios").get(1);
        assertThat(carla.get("convitePendente").asBoolean()).isFalse();
        assertThat(equipe.get("usuarios").get(0).get("voce").asBoolean()).isTrue();

        convidar(admin, "Outra", "carla.souza@TESTE.com", "GERENTE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Já existe um usuário com este e-mail no Nexus Food."));
    }

    @Test
    void soAdministradorGerenciaAEquipe() throws Exception {
        Sessao admin = api.registrar("eq-perfis@teste.com");
        Sessao gerente = entrarNaEquipe(admin, "Gerente", "eq-gerente@teste.com", "GERENTE");
        Sessao atendente = entrarNaEquipe(admin, "Atendente", "eq-atendente@teste.com", "ATENDENTE");

        api.chamar(gerente, get("/api/usuarios"), null).andExpect(status().isForbidden());
        api.chamar(atendente, get("/api/usuarios"), null).andExpect(status().isForbidden());
        convidar(gerente, "X", "eq-x@teste.com", "ADMINISTRADOR").andExpect(status().isForbidden());

        // Perfis continuam valendo nas outras telas.
        api.chamar(gerente, get("/api/relatorios/vendas"), null).andExpect(status().isOk());
        api.chamar(atendente, get("/api/relatorios/vendas"), null).andExpect(status().isForbidden());
        api.chamar(atendente, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());
    }

    @Test
    void limiteDeUsuariosDoPlano() throws Exception {
        Sessao admin = api.registrar("eq-limite@teste.com");
        plano("eq-limite@teste.com", PlanoSaas.BASICO);

        entrarNaEquipe(admin, "Segundo", "eq-limite-2@teste.com", "ATENDENTE");
        convidar(admin, "Terceiro", "eq-limite-3@teste.com", "ATENDENTE")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.upgradeNecessario").value(true))
                .andExpect(jsonPath("$.recurso").value("EQUIPE"))
                .andExpect(jsonPath("$.planoNecessario").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.limiteUsuarios").value(2));

        JsonNode equipe = api.json(api.chamar(admin, get("/api/usuarios"), null));
        assertThat(equipe.get("limiteUsuarios").asInt()).isEqualTo(2);
        assertThat(equipe.get("proximoPlano").asText()).isEqualTo("PROFISSIONAL");

        // Desativado não conta: libera a vaga, e reativar exige vaga de novo.
        long segundo = idDe(admin, "eq-limite-2@teste.com");
        api.chamar(admin, patch("/api/usuarios/" + segundo + "/ativo?valor=false"), null).andExpect(status().isOk());
        convidar(admin, "Terceiro", "eq-limite-3@teste.com", "ATENDENTE").andExpect(status().isOk());
        api.chamar(admin, patch("/api/usuarios/" + segundo + "/ativo?valor=true"), null).andExpect(status().isPaymentRequired());
    }

    @Test
    void desativarCortaOAcessoNaHora() throws Exception {
        Sessao admin = api.registrar("eq-desativa@teste.com");
        Sessao atendente = entrarNaEquipe(admin, "Atendente", "eq-desativa-at@teste.com", "ATENDENTE");
        api.chamar(atendente, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());

        long id = idDe(admin, "eq-desativa-at@teste.com");
        api.chamar(admin, patch("/api/usuarios/" + id + "/ativo?valor=false"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        // O token ainda não venceu, mas não vale mais.
        api.chamar(atendente, get("/api/pedidos/em-andamento"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Seu acesso foi desativado pelo administrador do restaurante."));
        mockMvc.perform(get("/api/pedidos/em-andamento"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Sua sessão terminou. Entre de novo."));
        login("eq-desativa-at@teste.com", "senha-forte-1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Seu acesso foi desativado pelo administrador do restaurante."));
        // Com a senha errada, não revela que a conta existe.
        login("eq-desativa-at@teste.com", "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));

        api.chamar(admin, patch("/api/usuarios/" + id + "/ativo?valor=true"), null).andExpect(status().isOk());
        login("eq-desativa-at@teste.com", "senha-forte-1").andExpect(status().isOk());
    }

    @Test
    void restauranteNuncaFicaSemAdministrador() throws Exception {
        Sessao dono = api.registrar("eq-admin@teste.com");
        long idDono = idDe(dono, "eq-admin@teste.com");

        api.chamar(dono, patch("/api/usuarios/" + idDono + "/ativo?valor=false"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Você não pode desativar o seu próprio acesso."));
        api.chamar(dono, put("/api/usuarios/" + idDono), "{\"nome\":\"Dono\",\"papel\":\"GERENTE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Você não pode mudar o seu próprio perfil. Peça a outro administrador."));
        // Mudar o próprio nome pode.
        api.chamar(dono, put("/api/usuarios/" + idDono), "{\"nome\":\"Dono Novo\",\"papel\":\"ADMINISTRADOR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Dono Novo"));

        Sessao socio = entrarNaEquipe(dono, "Sócia", "eq-admin-socia@teste.com", "ADMINISTRADOR");
        // Com dois administradores, um pode rebaixar o outro...
        api.chamar(socio, put("/api/usuarios/" + idDono), "{\"nome\":\"Dono\",\"papel\":\"GERENTE\"}").andExpect(status().isOk());
        // ...mas o último administrador ativo não pode ser desativado por ninguém.
        long idSocia = idDe(socio, "eq-admin-socia@teste.com");
        api.chamar(socio, patch("/api/usuarios/" + idSocia + "/ativo?valor=false"), null).andExpect(status().isBadRequest());
    }

    @Test
    void outroRestauranteNaoMexeNaEquipe() throws Exception {
        Sessao a = api.registrar("eq-tenant-a@teste.com");
        Sessao b = api.registrar("eq-tenant-b@teste.com");
        long idA = idDe(a, "eq-tenant-a@teste.com");

        api.chamar(b, put("/api/usuarios/" + idA), "{\"nome\":\"Invasor\",\"papel\":\"ATENDENTE\"}").andExpect(status().isNotFound());
        api.chamar(b, patch("/api/usuarios/" + idA + "/ativo?valor=false"), null).andExpect(status().isNotFound());
        api.chamar(b, post("/api/usuarios/" + idA + "/link"), null).andExpect(status().isNotFound());
        assertThat(api.json(api.chamar(b, get("/api/usuarios"), null)).get("usuarios")).hasSize(1);
    }

    @Test
    void novoLinkPermiteCriarOutraSenha() throws Exception {
        Sessao admin = api.registrar("eq-link@teste.com");
        entrarNaEquipe(admin, "Esquecida", "eq-link-2@teste.com", "GERENTE");
        long id = idDe(admin, "eq-link-2@teste.com");

        JsonNode novo = api.json(api.chamar(admin, post("/api/usuarios/" + id + "/link"), null).andExpect(status().isOk()));
        mockMvc.perform(get("/auth/convite").param("token", token(novo)))
                .andExpect(jsonPath("$.convite").value(false));
        mockMvc.perform(post("/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"novaSenha\":\"outra-senha\"}".formatted(token(novo))))
                .andExpect(status().isNoContent());
        login("eq-link-2@teste.com", "outra-senha").andExpect(status().isOk());

        api.chamar(admin, post("/api/usuarios/" + idDe(admin, "eq-link@teste.com") + "/link"), null).andExpect(status().isBadRequest());
    }

    @Test
    void planoMenorQueAEquipeDeixaOsMaisRecentesSemAcesso() throws Exception {
        Sessao dono = api.registrar("eq-downgrade@teste.com");
        plano("eq-downgrade@teste.com", PlanoSaas.PROFISSIONAL);
        Sessao segundo = entrarNaEquipe(dono, "Segundo", "eq-down-2@teste.com", "GERENTE");
        Sessao terceiro = entrarNaEquipe(dono, "Terceiro", "eq-down-3@teste.com", "ATENDENTE");

        // Pelo sistema, a troca para um plano menor que a equipe é recusada antes de cobrar.
        api.chamar(dono, post("/api/assinatura/plano?plano=BASICO"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O plano Básico permite 2 usuários ativos e sua equipe tem 3. Desative 1 usuário em Equipe antes de mudar."));

        // Se o plano diminuir por fora (portal da Stripe), quem entrou por último fica sem acesso.
        plano("eq-downgrade@teste.com", PlanoSaas.BASICO);
        api.chamar(dono, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());
        api.chamar(segundo, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());
        api.chamar(terceiro, get("/api/pedidos/em-andamento"), null)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.usuarioForaDoLimite").value(true));
        api.chamar(terceiro, get("/api/assinatura"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioForaDoLimite").value(true));

        JsonNode equipe = api.json(api.chamar(dono, get("/api/usuarios"), null));
        for (JsonNode u : equipe.get("usuarios")) {
            assertThat(u.get("foraDoLimite").asBoolean()).as(u.get("email").asText())
                    .isEqualTo(u.get("email").asText().equals("eq-down-3@teste.com"));
        }

        // O administrador desativa alguém e o acesso volta para quem ficou.
        api.chamar(dono, patch("/api/usuarios/" + idDe(dono, "eq-down-2@teste.com") + "/ativo?valor=false"), null).andExpect(status().isOk());
        api.chamar(terceiro, get("/api/pedidos/em-andamento"), null).andExpect(status().isOk());
    }
}
