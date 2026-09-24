package com.nexusfood.plataforma.acesso;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhuma rota de /api/** pode existir sem dizer de qual recurso do plano ela é (@RequerRecurso)
 * ou por que não depende de plano (@AcessoLivre). Com o antigo mapa de prefixos, esquecer uma
 * entrada liberava a rota sem aviso; aqui, esquecer a anotação quebra o build.
 */
@SpringBootTest
class RecursoAnotacaoTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamento;

    @Test
    void todaRotaDaApiDeclaraRecursoOuAcessoLivre() {
        List<String> semDeclaracao = new ArrayList<>();
        int rotasDaApi = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamento.getHandlerMethods().entrySet()) {
            boolean ehApi = entrada.getKey().getPatternValues().stream().anyMatch(p -> p.startsWith("/api/"));
            if (!ehApi) continue;
            rotasDaApi++;

            HandlerMethod metodo = entrada.getValue();
            boolean declarado = temAnotacao(metodo, RequerRecurso.class) || temAnotacao(metodo, AcessoLivre.class);
            if (!declarado) semDeclaracao.add(entrada.getKey().toString());
        }

        assertThat(rotasDaApi).as("rotas de /api encontradas").isGreaterThan(20);
        assertThat(semDeclaracao).as("rotas sem @RequerRecurso nem @AcessoLivre").isEmpty();
    }

    private static boolean temAnotacao(HandlerMethod metodo, Class<? extends java.lang.annotation.Annotation> tipo) {
        return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), tipo)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), tipo);
    }
}
