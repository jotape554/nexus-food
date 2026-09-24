package com.nexusfood.analytics.regras;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Carrega as versões da regra do score a partir dos arquivos em analytics/regras. */
@Component
public class RegrasNexus {

    private final ObjectMapper objectMapper;
    private final String versaoVigente;
    private final Map<String, RegraScore> cache = new ConcurrentHashMap<>();

    public RegrasNexus(ObjectMapper objectMapper, @Value("${app.nexus.regra-vigente:v1}") String versaoVigente) {
        this.objectMapper = objectMapper;
        this.versaoVigente = versaoVigente;
        vigente(); // falha na subida se o arquivo da regra vigente estiver ausente ou inválido
    }

    public RegraScore vigente() {
        return versao(versaoVigente);
    }

    public RegraScore versao(String versao) {
        return cache.computeIfAbsent(versao, v -> {
            try (InputStream in = new ClassPathResource("analytics/regras/nexus-score-" + v + ".json").getInputStream()) {
                return objectMapper.readValue(in, RegraScore.class);
            } catch (IOException e) {
                throw new UncheckedIOException("Regra do Nexus Score não encontrada ou inválida: " + v, e);
            }
        });
    }
}
