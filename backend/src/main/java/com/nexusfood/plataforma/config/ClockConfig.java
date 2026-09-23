package com.nexusfood.plataforma.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * "Agora" vem sempre deste bean, nunca de LocalDate.now()/Instant.now() soltos: os testes
 * trocam o Clock para simular virada do dia, fim de trial etc. sem depender do relógio real.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
