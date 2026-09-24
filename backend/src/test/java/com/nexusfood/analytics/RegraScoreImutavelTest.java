package com.nexusfood.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.analytics.regras.RegraScore;
import com.nexusfood.analytics.regras.RegrasNexus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Uma regra publicada nunca muda: as notas guardadas foram calculadas com ela. Se este teste
 * falhar porque você editou nexus-score-v1.json, desfaça a edição e crie nexus-score-v2.json.
 */
class RegraScoreImutavelTest {

    private static final String SHA256_V1 = "90acd5579f08c388a7ca3f1a5a3a89e4c995e1627b5b94fb24ae232db4d16bc7";

    @Test
    void regraV1PublicadaNaoFoiAlterada() throws Exception {
        try (InputStream in = new ClassPathResource("analytics/regras/nexus-score-v1.json").getInputStream()) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            assertThat(hash).isEqualTo(SHA256_V1);
        }
    }

    @Test
    void pesosSomamUmEmCadaNivel() {
        RegraScore v1 = new RegrasNexus(new ObjectMapper(), "v1").vigente();
        assertThat(v1.areas().stream().mapToDouble(RegraScore.Area::peso).sum()).isCloseTo(1.0, within(1e-9));
        v1.areas().forEach(a -> assertThat(a.indicadores().stream().mapToDouble(RegraScore.Indicador::peso).sum())
                .as(a.codigo()).isCloseTo(1.0, within(1e-9)));
    }
}
