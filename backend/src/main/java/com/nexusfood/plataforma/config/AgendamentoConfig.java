package com.nexusfood.plataforma.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as rotinas agendadas (ex.: NexusJob). Desligue um job pela propriedade dele, não aqui. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
