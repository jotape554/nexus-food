package com.nexusfood.plataforma.acesso;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rota de /api/** que não depende do plano (assinatura, configurações do restaurante). Existe
 * para a ausência de {@link RequerRecurso} ser sempre uma decisão, nunca um esquecimento.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AcessoLivre {
    /** Por que esta rota não depende do plano. */
    String value();
}
