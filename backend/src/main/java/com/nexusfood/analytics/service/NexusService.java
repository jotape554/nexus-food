package com.nexusfood.analytics.service;

import com.nexusfood.analytics.dto.NexusResponse;
import com.nexusfood.analytics.model.NexusScore;
import com.nexusfood.analytics.model.NexusScoreArea;
import com.nexusfood.analytics.model.NexusScoreIndicador;
import com.nexusfood.analytics.model.SnapshotDiario;
import com.nexusfood.analytics.regras.RegraScore;
import com.nexusfood.analytics.regras.RegrasNexus;
import com.nexusfood.analytics.repository.AnalyticsRepository;
import com.nexusfood.analytics.repository.NexusScoreRepository;
import com.nexusfood.analytics.repository.SnapshotDiarioRepository;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.service.RelogioRestaurante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orquestra o Nexus de um restaurante: resumos diários → nota do dia → insights.
 *
 * O dia de referência é sempre o último dia operacional FECHADO (ontem, no fuso e na virada do
 * restaurante). Tudo é idempotente: processar de novo o mesmo dia substitui, nunca duplica.
 */
@Service
@RequiredArgsConstructor
public class NexusService {

    /** Dias recentes que sempre são recalculados (pedido concluído/cancelado depois da virada). */
    static final int DIAS_RECALCULO = 3;
    /** Quantos dias de nota ficam disponíveis no gráfico de evolução. */
    static final int DIAS_EVOLUCAO = 90;

    private final RestauranteRepository restauranteRepository;
    private final AnalyticsRepository analyticsRepository;
    private final SnapshotDiarioRepository snapshotRepository;
    private final NexusScoreRepository scoreRepository;
    private final SnapshotService snapshotService;
    private final ColetorIndicadores coletor;
    private final NexusScoreEngine engine;
    private final InsightService insightService;
    private final RegrasNexus regras;
    private final RelogioRestaurante relogio;
    private final Clock clock;

    public LocalDate diaDeReferencia(Restaurante restaurante) {
        return relogio.diaOperacionalAtual(restaurante).minusDays(1);
    }

    /** Garante resumos, notas (inclusive as dos dias sem nota ainda, para a evolução) e insights até ontem. */
    @Transactional
    public void processar(Long restauranteId) {
        Restaurante restaurante = buscar(restauranteId);
        LocalDate dia = diaDeReferencia(restaurante);
        LocalDate primeiro = analyticsRepository.primeiroDiaComPedido(restauranteId);
        ZoneId fuso = ZoneId.of(restaurante.getFusoHorario());
        RegraScore regra = regras.vigente();

        if (primeiro != null && !primeiro.isAfter(dia)) {
            Set<LocalDate> prontos = new HashSet<>(snapshotRepository.diasComSnapshot(restauranteId, primeiro, dia, SnapshotDiario.VERSAO_CALCULO));
            LocalDate recalcularDesde = dia.minusDays(DIAS_RECALCULO - 1L);
            for (LocalDate d = primeiro; !d.isAfter(dia); d = d.plusDays(1)) {
                if (!prontos.contains(d) || !d.isBefore(recalcularDesde)) snapshotService.gerarDia(restauranteId, d, fuso);
            }
            // Notas passadas que faltam (evolução); a de hoje é sempre recalculada.
            LocalDate inicioEvolucao = max(primeiro.plusDays(regra.historico().diasMinimosNota() - 1L), dia.minusDays(DIAS_EVOLUCAO - 1L));
            for (LocalDate d = inicioEvolucao; d.isBefore(dia); d = d.plusDays(1)) {
                if (!scoreRepository.existsByRestauranteIdAndDiaAndRegraVersao(restauranteId, d, regra.versao())) {
                    calcularNota(restauranteId, d, fuso, regra);
                }
            }
        }
        ColetorIndicadores.Coleta coleta = calcularNota(restauranteId, dia, fuso, regra);
        insightService.gerar(restauranteId, coleta);
    }

    /** Tela do Nexus. Se a nota de ontem ainda não foi calculada (o job roda de hora em hora), calcula agora. */
    @Transactional
    public NexusResponse painel(Long restauranteId) {
        Restaurante restaurante = buscar(restauranteId);
        LocalDate dia = diaDeReferencia(restaurante);
        RegraScore regra = regras.vigente();
        if (!scoreRepository.existsByRestauranteIdAndDiaAndRegraVersao(restauranteId, dia, regra.versao())) {
            processar(restauranteId);
        }
        NexusScore score = scoreRepository.findByRestauranteIdAndDiaAndRegraVersao(restauranteId, dia, regra.versao()).orElseThrow();
        List<NexusScore> historico = scoreRepository.findAllByRestauranteIdAndRegraVersaoAndDiaBetweenOrderByDia(
                restauranteId, regra.versao(), dia.minusDays(DIAS_EVOLUCAO - 1L), dia);
        return montarResposta(score, regra, historico, insightService.ativos(restauranteId, dia));
    }

    private ColetorIndicadores.Coleta calcularNota(Long restauranteId, LocalDate dia, ZoneId fuso, RegraScore regra) {
        ColetorIndicadores.Coleta coleta = coletor.coletar(restauranteId, dia, fuso);
        NexusScoreEngine.Resultado r = engine.calcular(regra, coleta.entrada());

        scoreRepository.findByRestauranteIdAndDiaAndRegraVersao(restauranteId, dia, regra.versao()).ifPresent(antigo -> {
            scoreRepository.delete(antigo);
            scoreRepository.flush();
        });

        NexusScore score = NexusScore.builder()
                .restauranteId(restauranteId)
                .dia(dia)
                .regraVersao(r.regraVersao())
                .situacao(r.situacao())
                .nota(r.nota())
                .faixa(r.faixa())
                .pesoValido(decimal(r.pesoValido(), 4))
                .diasHistorico(r.diasHistorico())
                .pedidosConcluidosJanela((int) r.pedidosConcluidosJanela())
                .motivoSemNota(r.motivoSemNota())
                .calculadoEm(Instant.now(clock))
                .build();
        r.areas().forEach(a -> score.getAreas().add(NexusScoreArea.builder()
                .score(score).area(a.codigo()).nota(a.nota() == null ? null : decimal(a.nota(), 1))
                .peso(decimal(a.peso(), 4)).pesoValido(decimal(a.pesoValido(), 4)).exibida(a.exibida()).build()));
        r.indicadores().forEach(i -> score.getIndicadores().add(NexusScoreIndicador.builder()
                .score(score).area(i.area()).codigo(i.codigo())
                .valor(i.valor() == null ? null : decimal(i.valor(), 4))
                .pontos(i.pontos() == null ? null : decimal(i.pontos(), 1))
                .pesoEfetivo(decimal(i.pesoEfetivo(), 4)).status(i.status()).motivo(i.motivo()).amostra(i.amostra()).build()));
        scoreRepository.save(score);
        return coleta;
    }

    private NexusResponse montarResposta(NexusScore s, RegraScore regra, List<NexusScore> historico,
                                         List<com.nexusfood.analytics.model.NexusInsight> insights) {
        RegraScore.Historico h = regra.historico();
        Map<String, NexusScoreArea> areas = s.getAreas().stream().collect(Collectors.toMap(NexusScoreArea::getArea, Function.identity()));
        Map<String, NexusScoreIndicador> indicadores = s.getIndicadores().stream()
                .collect(Collectors.toMap(NexusScoreIndicador::getCodigo, Function.identity()));

        List<NexusResponse.Area> areasResposta = regra.areas().stream().map(a -> {
            NexusScoreArea salva = areas.get(a.codigo());
            List<NexusResponse.Indicador> inds = a.indicadores().stream().map(ind -> {
                NexusScoreIndicador x = indicadores.get(ind.codigo());
                return new NexusResponse.Indicador(ind.codigo(), ind.nome(), ind.descricao(), ind.unidade(),
                        x == null || x.getValor() == null ? null : x.getValor().setScale(2, RoundingMode.HALF_UP),
                        x == null ? null : x.getPontos(), BigDecimal.valueOf(ind.peso()), x == null ? BigDecimal.ZERO : x.getPesoEfetivo(),
                        x == null ? "SEM_DADOS" : x.getStatus().name(), x == null ? null : x.getMotivo(), x == null ? 0 : x.getAmostra(),
                        new NexusResponse.Ancoras(ind.ancoras().zero(), ind.ancoras().referencia(), ind.ancoras().cem()));
            }).toList();
            return new NexusResponse.Area(a.codigo(), a.nome(), BigDecimal.valueOf(a.peso()),
                    salva == null ? null : salva.getNota(), salva != null && salva.isExibida(), inds);
        }).toList();

        Integer variacao = historico.stream().filter(x -> x.getDia().equals(s.getDia().minusDays(7)) && x.getNota() != null)
                .findFirst().filter(x -> s.getNota() != null).map(x -> s.getNota() - x.getNota()).orElse(null);

        return new NexusResponse(
                s.getDia(), s.getDia().minusDays(ColetorIndicadores.JANELA - 1L), s.getRegraVersao(), s.getSituacao().name(),
                s.getNota(), s.getFaixa(), s.getFaixa() == null ? null : regra.faixaDaNota(s.getNota()).nome(), s.getMotivoSemNota(),
                s.getPesoValido(), s.getDiasHistorico(),
                Math.max(0, h.diasMinimosNota() - s.getDiasHistorico()), Math.max(0, h.diasNotaOficial() - s.getDiasHistorico()),
                s.getPedidosConcluidosJanela(), h.pedidosConcluidosMinimos(), variacao,
                regra.faixas().stream().map(f -> new NexusResponse.Faixa(f.codigo(), f.nome(), f.de(), f.ate())).toList(),
                areasResposta,
                historico.stream().map(x -> new NexusResponse.PontoHistorico(x.getDia(), x.getNota(), x.getSituacao().name())).toList(),
                insights.stream().map(i -> new NexusResponse.Insight(i.getId(), i.getDia(), i.getSeveridade().name(), i.getTexto())).toList());
    }

    private Restaurante buscar(Long id) {
        return restauranteRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }

    private static BigDecimal decimal(double v, int casas) {
        return BigDecimal.valueOf(v).setScale(casas, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
