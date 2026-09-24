package com.nexusfood.analytics.service;

import com.nexusfood.plataforma.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda de hora em hora e fecha o dia de cada restaurante: como cada um tem seu fuso e sua hora de
 * virada, "ontem" chega em horários diferentes. Processar de novo é seguro (idempotente).
 * Um restaurante com erro não impede os outros.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusJob {

    private final RestauranteRepository restauranteRepository;
    private final NexusService nexusService;

    @Value("${app.nexus.job.ativo:true}")
    private boolean ativo;

    @Scheduled(fixedDelayString = "${app.nexus.job.intervalo-ms:3600000}", initialDelayString = "${app.nexus.job.atraso-inicial-ms:120000}")
    public void executarAgendado() {
        if (ativo) executar();
    }

    public int executar() {
        int processados = 0;
        for (Long id : restauranteRepository.findAll().stream().map(r -> r.getId()).toList()) {
            try {
                nexusService.processar(id);
                processados++;
            } catch (RuntimeException e) {
                log.error("Nexus: falha ao processar o restaurante {}", id, e);
            }
        }
        return processados;
    }
}
