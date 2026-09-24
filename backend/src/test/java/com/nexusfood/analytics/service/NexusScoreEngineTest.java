package com.nexusfood.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.analytics.regras.RegraScore;
import com.nexusfood.analytics.regras.RegrasNexus;
import com.nexusfood.analytics.service.NexusScoreEngine.Entrada;
import com.nexusfood.analytics.service.NexusScoreEngine.Medida;
import com.nexusfood.analytics.service.NexusScoreEngine.Resultado;
import com.nexusfood.analytics.service.NexusScoreEngine.ResultadoIndicador;
import com.nexusfood.analytics.service.NexusScoreEngine.Situacao;
import com.nexusfood.analytics.service.NexusScoreEngine.StatusIndicador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NexusScoreEngineTest {

    private final RegraScore v1 = new RegrasNexus(new ObjectMapper(), "v1").vigente();
    private final NexusScoreEngine engine = new NexusScoreEngine();

    // ---------- pontos por indicador ----------

    @ParameterizedTest(name = "crescimento {0}% → {1} pontos")
    @CsvSource({"-40, 0", "-25, 0", "-12.5, 30", "0, 60", "7.5, 80", "15, 100", "30, 100"})
    void maiorEMelhorInterpolaEntreAsAncoras(double valor, double pontos) {
        assertThat(NexusScoreEngine.pontos(new RegraScore.Ancoras(-25, 0, 15), valor)).isEqualTo(pontos);
    }

    @ParameterizedTest(name = "cancelamento {0}% → {1} pontos")
    @CsvSource({"20, 0", "12, 0", "8.5, 30", "5, 60", "3.5, 80", "2, 100", "0, 100"})
    void menorEMelhorInterpolaAoContrario(double valor, double pontos) {
        assertThat(NexusScoreEngine.pontos(new RegraScore.Ancoras(12, 5, 2), valor)).isEqualTo(pontos);
    }

    // ---------- cenário completo ----------

    /** Um restaurante estável: todos os indicadores exatamente na referência (60 pontos). */
    private Map<String, Medida> tudoNaReferencia() {
        Map<String, Medida> m = new HashMap<>();
        m.put("V1_CRESCIMENTO_FATURAMENTO", new Medida(0.0, 200, null));
        m.put("V2_EVOLUCAO_TICKET", new Medida(0.0, 200, null));
        m.put("V3_REGULARIDADE", new Medida(0.25, 200, null));
        m.put("C1_RECOMPRA", new Medida(30.0, 120, null));
        m.put("C2_RETENCAO_NOVOS", new Medida(20.0, 40, null));
        m.put("C3_BASE_ATIVA", new Medida(0.0, 200, null));
        m.put("O1_CANCELAMENTO_RESTAURANTE", new Medida(5.0, 210, null));
        m.put("O2_TEMPO_ACEITE", new Medida(5.0, 210, 1.0));
        m.put("O3_PONTUALIDADE", new Medida(85.0, 200, 0.9));
        m.put("K1_PRODUTOS_PARADOS", new Medida(30.0, 20, null));
        return m;
    }

    @Test
    void restauranteEstavelTiraSessentaNaFaixaBom() {
        Resultado r = engine.calcular(v1, new Entrada(90, 200, tudoNaReferencia()));
        assertThat(r.situacao()).isEqualTo(Situacao.OFICIAL);
        assertThat(r.nota()).isEqualTo(60);
        assertThat(r.faixa()).isEqualTo("BOM");
        assertThat(r.pesoValido()).isCloseTo(1.0, within(1e-9));
        assertThat(r.indicadores()).allMatch(i -> i.status() == StatusIndicador.OK);
        assertThat(r.indicadores().stream().mapToDouble(ResultadoIndicador::pesoEfetivo).sum()).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void indicadorSemDadosNaoViraZeroEOPesoERedistribuido() {
        Map<String, Medida> m = tudoNaReferencia();
        m.put("C1_RECOMPRA", new Medida(50.0, 120, null));   // 100 pontos
        m.put("C2_RETENCAO_NOVOS", new Medida(90.0, 3, null)); // amostra 3 < 15 → sem dados
        Resultado r = engine.calcular(v1, new Entrada(90, 200, m));

        ResultadoIndicador c2 = r.indicadores().stream().filter(i -> i.codigo().equals("C2_RETENCAO_NOVOS")).findFirst().orElseThrow();
        assertThat(c2.status()).isEqualTo(StatusIndicador.SEM_DADOS);
        assertThat(c2.pontos()).isNull();
        assertThat(c2.motivo()).contains("3 de 15");
        // Clientes = (100×0,5 + 60×0,2) / 0,7 = 88,6
        assertThat(r.areas().get(1).nota()).isEqualTo(88.6);
        // Nota = (60×0,35 + 88,6×0,30 + 60×0,25 + 60×0,10) / 1 = 68,58 → 69
        assertThat(r.nota()).isEqualTo(69);
    }

    @Test
    void areaComMenosDaMetadeDoPesoSomeEAsOutrasAssumem() {
        Map<String, Medida> m = tudoNaReferencia();
        m.put("K1_PRODUTOS_PARADOS", new Medida(0.0, 2, null)); // poucos produtos → Cardápio some
        m.put("O2_TEMPO_ACEITE", new Medida(2.0, 210, 1.0));    // 100 pontos
        Resultado r = engine.calcular(v1, new Entrada(90, 200, m));

        assertThat(r.areas().get(3).exibida()).isFalse();
        assertThat(r.areas().get(3).nota()).isNull();
        // Operação = 60×0,45 + 100×0,30 + 60×0,25 = 72; nota = (60×0,35 + 60×0,30 + 72×0,25) / 0,9 = 63,3
        assertThat(r.areas().get(2).nota()).isEqualTo(72.0);
        assertThat(r.nota()).isEqualTo(63);
        assertThat(r.pesoValido()).isCloseTo(0.9, within(1e-9));
    }

    @Test
    void antesDe28DiasNaoHaNota() {
        Resultado r = engine.calcular(v1, new Entrada(20, 200, tudoNaReferencia()));
        assertThat(r.situacao()).isEqualTo(Situacao.COLETANDO);
        assertThat(r.nota()).isNull();
        assertThat(r.motivoSemNota()).contains("faltam 8");
    }

    @Test
    void comPoucosPedidosNaoHaNota() {
        Resultado r = engine.calcular(v1, new Entrada(90, 45, tudoNaReferencia()));
        assertThat(r.situacao()).isEqualTo(Situacao.COLETANDO);
        assertThat(r.motivoSemNota()).contains("60 pedidos").contains("45");
    }

    @Test
    void entre28e55DiasANotaEProvisoriaSemOsIndicadoresDeComparacao() {
        Resultado r = engine.calcular(v1, new Entrada(35, 200, tudoNaReferencia()));
        assertThat(r.situacao()).isEqualTo(Situacao.PROVISORIA);
        assertThat(r.nota()).isEqualTo(60);
        for (String codigo : new String[]{"V1_CRESCIMENTO_FATURAMENTO", "V2_EVOLUCAO_TICKET", "C2_RETENCAO_NOVOS", "C3_BASE_ATIVA"}) {
            ResultadoIndicador i = r.indicadores().stream().filter(x -> x.codigo().equals(codigo)).findFirst().orElseThrow();
            assertThat(i.status()).as(codigo).isEqualTo(StatusIndicador.SEM_DADOS);
            assertThat(i.motivo()).as(codigo).contains("56 dias").contains("faltam 21");
        }
        // Vendas só com V3 (25% da área) → some; Clientes só com C1 (50%) → aparece
        assertThat(r.areas().get(0).exibida()).isFalse();
        assertThat(r.areas().get(1).exibida()).isTrue();
        assertThat(r.pesoValido()).as("Clientes + Operação + Cardápio").isCloseTo(0.65, within(1e-9));
    }

    @Test
    void semCoberturaDeHorarioOIndicadorDeTempoNaoVale() {
        Map<String, Medida> m = tudoNaReferencia();
        m.put("O3_PONTUALIDADE", new Medida(100.0, 200, 0.4));
        Resultado r = engine.calcular(v1, new Entrada(90, 200, m));
        ResultadoIndicador o3 = r.indicadores().stream().filter(i -> i.codigo().equals("O3_PONTUALIDADE")).findFirst().orElseThrow();
        assertThat(o3.status()).isEqualTo(StatusIndicador.SEM_DADOS);
        assertThat(o3.motivo()).contains("40%").contains("60%");
    }

    @ParameterizedTest(name = "nota {0} → {1}")
    @CsvSource({"0, CRITICO", "39, CRITICO", "40, ATENCAO", "59, ATENCAO", "60, BOM", "74, BOM", "75, MUITO_BOM", "89, MUITO_BOM", "90, EXCELENTE", "100, EXCELENTE"})
    void faixasDaNota(int nota, String faixa) {
        assertThat(v1.faixaDaNota(nota).codigo()).isEqualTo(faixa);
    }
}
