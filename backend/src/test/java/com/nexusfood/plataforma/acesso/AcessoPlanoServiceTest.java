package com.nexusfood.plataforma.acesso;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
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
class AcessoPlanoServiceTest {

    @Mock private RestauranteRepository restauranteRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);

    private AcessoPlanoService service() {
        return new AcessoPlanoService(restauranteRepository, usuarioRepository, clock);
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

    // ---------- plano efetivo ----------

    private Restaurante ativo(PlanoSaas plano) {
        Restaurante r = comStatus(StatusAssinaturaSaas.ATIVA, null);
        r.setPlanoSaas(plano);
        return r;
    }

    @Test
    void testeGratisValeComoPremium() {
        Restaurante trial = comStatus(StatusAssinaturaSaas.TRIAL, LocalDate.now(clock).plusDays(3));
        trial.setPlanoSaas(PlanoSaas.BASICO);
        assertThat(service().planoEfetivo(trial)).isEqualTo(PlanoSaas.PREMIUM);
        assertThat(service().liberado(trial, Recurso.NEXUS_DETALHES)).isTrue();
    }

    @Test
    void semAssinaturaValendoNaoHaPlanoNemRecurso() {
        Restaurante vencido = comStatus(StatusAssinaturaSaas.TRIAL, LocalDate.now(clock).minusDays(1));
        assertThat(service().planoEfetivo(vencido)).isNull();
        assertThat(service().liberado(vencido, Recurso.PEDIDOS)).isFalse();
    }

    @Test
    void matrizDeRecursosPorPlano() {
        assertThat(service().liberado(ativo(PlanoSaas.BASICO), Recurso.PEDIDOS)).isTrue();
        assertThat(service().liberado(ativo(PlanoSaas.BASICO), Recurso.RELATORIOS)).isTrue();
        assertThat(service().liberado(ativo(PlanoSaas.BASICO), Recurso.NEXUS_SCORE)).isFalse();
        assertThat(service().liberado(ativo(PlanoSaas.PROFISSIONAL), Recurso.NEXUS_SCORE)).isTrue();
        assertThat(service().liberado(ativo(PlanoSaas.PROFISSIONAL), Recurso.NEXUS_DETALHES)).isFalse();
        assertThat(service().liberado(ativo(PlanoSaas.PREMIUM), Recurso.NEXUS_DETALHES)).isTrue();
    }

    @Test
    void historicoDoRelatorioPorPlano() {
        LocalDate hoje = LocalDate.of(2026, 9, 24);
        // Básico: últimos 30 dias (e o mês atual inteiro, que aqui já está dentro dos 30).
        assertThat(PlanoSaas.BASICO.primeiroDiaDoHistorico(hoje)).isEqualTo(LocalDate.of(2026, 8, 26));
        // No dia 31, "este mês" começa 30 dias atrás: o dia 1 continua liberado.
        assertThat(PlanoSaas.BASICO.primeiroDiaDoHistorico(LocalDate.of(2026, 10, 31))).isEqualTo(LocalDate.of(2026, 10, 1));
        // Profissional: o último ano, cobrindo sempre o atalho "12 meses" (dia 1 de 11 meses atrás).
        assertThat(PlanoSaas.PROFISSIONAL.primeiroDiaDoHistorico(hoje)).isEqualTo(LocalDate.of(2025, 9, 25));
        assertThat(PlanoSaas.PROFISSIONAL.primeiroDiaDoHistorico(LocalDate.of(2028, 3, 31))).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(PlanoSaas.PREMIUM.primeiroDiaDoHistorico(hoje)).isNull();

        assertThat(PlanoSaas.menorPlanoComHistoricoDesde(LocalDate.of(2026, 9, 1), hoje)).isEqualTo(PlanoSaas.BASICO);
        assertThat(PlanoSaas.menorPlanoComHistoricoDesde(LocalDate.of(2026, 8, 1), hoje)).isEqualTo(PlanoSaas.PROFISSIONAL);
        assertThat(PlanoSaas.menorPlanoComHistoricoDesde(LocalDate.of(2024, 1, 1), hoje)).isEqualTo(PlanoSaas.PREMIUM);
    }

    @Test
    void limiteDeUsuariosPorPlano() {
        assertThat(PlanoSaas.menorPlanoParaUsuarios(2)).isEqualTo(PlanoSaas.BASICO);
        assertThat(PlanoSaas.menorPlanoParaUsuarios(3)).isEqualTo(PlanoSaas.PROFISSIONAL);
        assertThat(PlanoSaas.menorPlanoParaUsuarios(6)).isEqualTo(PlanoSaas.PREMIUM);
    }
}
