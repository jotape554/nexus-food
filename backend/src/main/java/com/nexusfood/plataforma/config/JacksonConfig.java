package com.nexusfood.plataforma.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sem isso, o Jackson tenta introspectar proxies não inicializados do Hibernate
 * (associações @ManyToOne/@OneToOne LAZY) e falha com
 * "No serializer found for class ...ByteBuddyInterceptor" ao serializar entidades
 * JPA diretamente como resposta de controller.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }
}
