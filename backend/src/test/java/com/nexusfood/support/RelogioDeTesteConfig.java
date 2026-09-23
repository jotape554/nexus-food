package com.nexusfood.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class RelogioDeTesteConfig {

    /** Quarta-feira, 23/09/2026, 15:00 em São Paulo. */
    public static final Instant INICIO = Instant.parse("2026-09-23T18:00:00Z");

    @Bean
    @Primary
    public RelogioDeTeste relogioDeTeste() {
        return new RelogioDeTeste(INICIO);
    }
}
