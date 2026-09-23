package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.config.StripeConfig;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra que decide se um restaurante inadimplente é barrado do painel. Um bug aqui ou libera
 * acesso de graça para sempre, ou bloqueia quem está pagando — os dois são graves, então a
 * lógica pura (sem precisar de banco nem de rede) fica coberta aqui.
 */
@ExtendWith(MockitoExtension.class)
class AssinaturaServiceAcessoTest {

    @Mock private RestauranteRepository restauranteRepository;
    @Mock private StripeConfig stripeConfig;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);

    private AssinaturaService service() {
        return new AssinaturaService(restauranteRepository, stripeConfig, clock);
    }

    private Restaurante comStatus(StatusAssinaturaSaas status, LocalDate dataFimTrial) {
        return Restaurante.builder()
                .nome("Restaurante Teste")
                .slug("restaurante-teste")
                .statusAssinaturaSaas(status)
                .dataFimTrial(dataFimTrial)
                .build();
    }

    @Test
    void assinaturaAtivaSempreLiberaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.ATIVA, null);
        assertThat(service().acessoLiberado(restaurante)).isTrue();
    }

    @Test
    void trialDentroDoPrazoLiberaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.TRIAL, LocalDate.now(clock).plusDays(1));
        assertThat(service().acessoLiberado(restaurante)).isTrue();
    }

    @Test
    void trialNoUltimoDiaAindaLiberaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.TRIAL, LocalDate.now(clock));
        assertThat(service().acessoLiberado(restaurante)).isTrue();
    }

    @Test
    void trialExpiradoOntemBloqueiaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.TRIAL, LocalDate.now(clock).minusDays(1));
        assertThat(service().acessoLiberado(restaurante)).isFalse();
    }

    @Test
    void trialSemDataDefinidaBloqueiaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.TRIAL, null);
        assertThat(service().acessoLiberado(restaurante)).isFalse();
    }

    @Test
    void statusInativaBloqueiaAcessoMesmoComTrialNoPrazo() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.INATIVA, LocalDate.now(clock).plusDays(5));
        assertThat(service().acessoLiberado(restaurante)).isFalse();
    }

    @Test
    void statusCanceladaBloqueiaAcesso() {
        Restaurante restaurante = comStatus(StatusAssinaturaSaas.CANCELADA, LocalDate.now(clock).plusDays(5));
        assertThat(service().acessoLiberado(restaurante)).isFalse();
    }
}
