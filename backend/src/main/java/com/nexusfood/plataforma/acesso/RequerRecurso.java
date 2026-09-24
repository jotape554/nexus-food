package com.nexusfood.plataforma.acesso;

import com.nexusfood.plataforma.enums.Recurso;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Recurso do plano que a rota usa. Na classe vale para todos os métodos; no método, sobrepõe
 * o da classe. Quem aplica é o {@link RecursoInterceptor}; quem garante que nenhuma rota de
 * /api/** fica sem declaração é o RecursoAnotacaoTest.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerRecurso {
    Recurso value();
}
