package com.nexusfood.relatorios.service;

import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.plataforma.acesso.AcessoPlanoService;
import com.nexusfood.plataforma.acesso.PlanoInsuficienteException;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.service.RelogioRestaurante;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.Comparacao;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.Faixa;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.Fatia;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.PontoSerie;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.Produto;
import com.nexusfood.relatorios.dto.RelatorioVendasResponse.Resumo;
import com.nexusfood.relatorios.model.Agrupamento;
import com.nexusfood.relatorios.model.PedidoResumo;
import com.nexusfood.relatorios.model.ProdutoVendido;
import com.nexusfood.relatorios.repository.RelatorioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Relatório de vendas por período. Os dias são sempre dias operacionais (fuso + hora de virada
 * do restaurante), então o relatório de "sexta" inclui a madrugada de sábado.
 */
@Service
@RequiredArgsConstructor
public class RelatorioVendasService {

    public static final int DIAS_MAXIMOS = 366;
    static final int DIAS_PADRAO = 30;
    static final int TOP_PRODUTOS = 10;

    private final RelatorioRepository relatorioRepository;
    private final RestauranteRepository restauranteRepository;
    private final RelogioRestaurante relogio;
    private final MetricasCalculator calculadora;
    private final AcessoPlanoService acessoPlano;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public RelatorioVendasResponse gerar(LocalDate inicioPedido, LocalDate fimPedido, Agrupamento agrupamentoPedido) {
        Long restauranteId = SecurityUtils.restauranteAtualId();
        Restaurante restaurante = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));

        LocalDate hoje = relogio.diaOperacionalAtual(restaurante);
        LocalDate fim = fimPedido != null ? fimPedido : hoje;
        LocalDate inicio = inicioPedido != null ? inicioPedido : fim.minusDays(DIAS_PADRAO - 1L);
        Agrupamento agrupamento = agrupamentoPedido != null ? agrupamentoPedido : Agrupamento.DIA;

        if (fim.isBefore(inicio)) {
            throw new RegraDeNegocioException("A data final não pode ser anterior à data inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > DIAS_MAXIMOS) {
            throw new RegraDeNegocioException("Escolha um período de até 1 ano.");
        }

        // O histórico que cada plano lê. A comparação com o período anterior continua valendo
        // (é um número de referência, não uma consulta ao histórico).
        PlanoSaas plano = acessoPlano.planoEfetivo(restaurante);
        LocalDate primeiroDiaPermitido = plano == null ? hoje : plano.primeiroDiaDoHistorico(hoje);
        if (primeiroDiaPermitido != null && inicio.isBefore(primeiroDiaPermitido)) {
            PlanoSaas necessario = PlanoSaas.menorPlanoComHistoricoDesde(inicio, hoje);
            throw new PlanoInsuficienteException(Recurso.RELATORIOS.name(), necessario,
                    "Seu plano mostra relatórios a partir de %s. Períodos mais antigos fazem parte do plano %s."
                            .formatted(DATA.format(primeiroDiaPermitido), AcessoPlanoService.nome(necessario)),
                    Map.of("primeiroDiaPermitido", primeiroDiaPermitido.toString()));
        }

        List<PedidoResumo> pedidos = relatorioRepository.pedidosDoPeriodo(restauranteId, inicio, fim);
        List<ProdutoVendido> produtos = relatorioRepository.produtosVendidos(restauranteId, inicio, fim, StatusPedido.CONCLUIDO);
        long clientesNovos = relatorioRepository.clientesNovos(restauranteId, inicio, fim, StatusPedido.CONCLUIDO);

        MetricasCalculator.Totais totais = calculadora.totais(pedidos);
        long itensVendidos = produtos.stream().mapToLong(ProdutoVendido::quantidade).sum();

        Resumo resumo = new Resumo(
                totais.faturamento(), totais.pedidosConcluidos(), totais.pedidosRecebidos(), totais.ticketMedio(),
                totais.cancelados(), totais.canceladosPeloRestaurante(), totais.taxaCancelamento(), itensVendidos,
                totais.clientesUnicos(), clientesNovos, Math.max(0, totais.clientesUnicos() - clientesNovos));

        return new RelatorioVendasResponse(
                hoje, primeiroDiaPermitido, inicio, fim, agrupamento, resumo,
                comparar(restauranteId, inicio, fim, totais),
                serie(pedidos, inicio, fim, agrupamento),
                fatias(calculadora.porChave(pedidos, p -> p.modalidade().name()), totais.faturamento()),
                fatias(calculadora.porChave(pedidos, p -> p.formaPagamento().name()), totais.faturamento()),
                faixas(calculadora.porHora(pedidos, ZoneId.of(restaurante.getFusoHorario()))),
                faixas(calculadora.porDiaDaSemana(pedidos)),
                produtos.stream().limit(TOP_PRODUTOS)
                        .map(p -> new Produto(p.produtoId(), p.nome(), p.quantidade(), p.faturamento(),
                                MetricasCalculator.percentual(p.faturamento(), totais.faturamento())))
                        .toList());
    }

    /**
     * Período de comparação. Começando no dia 1, compara com o mesmo trecho do(s) mês(es)
     * anterior(es) — "1 a 24 de setembro" contra "1 a 24 de agosto", "setembro" contra "agosto".
     * Nos demais casos, com o mesmo número de dias imediatamente antes.
     */
    static LocalDate[] periodoAnterior(LocalDate inicio, LocalDate fim) {
        if (inicio.getDayOfMonth() == 1) {
            long meses = ChronoUnit.MONTHS.between(YearMonth.from(inicio), YearMonth.from(fim)) + 1;
            LocalDate fimAnterior = fim.equals(fim.withDayOfMonth(fim.lengthOfMonth()))
                    ? YearMonth.from(fim).minusMonths(meses).atEndOfMonth()
                    : fim.minusMonths(meses);
            return new LocalDate[]{inicio.minusMonths(meses), fimAnterior};
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        return new LocalDate[]{inicio.minusDays(dias), inicio.minusDays(1)};
    }

    private Comparacao comparar(Long restauranteId, LocalDate inicio, LocalDate fim, MetricasCalculator.Totais atual) {
        LocalDate[] anterior = periodoAnterior(inicio, fim);
        MetricasCalculator.Totais antes = calculadora.totais(relatorioRepository.pedidosDoPeriodo(restauranteId, anterior[0], anterior[1]));
        return new Comparacao(
                anterior[0], anterior[1], antes.faturamento(), antes.pedidosConcluidos(), antes.ticketMedio(),
                MetricasCalculator.variacao(atual.faturamento(), antes.faturamento()),
                MetricasCalculator.variacao(BigDecimal.valueOf(atual.pedidosConcluidos()), BigDecimal.valueOf(antes.pedidosConcluidos())),
                MetricasCalculator.variacao(atual.ticketMedio(), antes.ticketMedio()));
    }

    /** Um ponto para cada dia/semana/mês do período — inclusive os sem venda, para o gráfico não pular datas. */
    private List<PontoSerie> serie(List<PedidoResumo> pedidos, LocalDate inicio, LocalDate fim, Agrupamento agrupamento) {
        Map<LocalDate, List<PedidoResumo>> porGrupo = pedidos.stream()
                .collect(Collectors.groupingBy(p -> agrupamento.inicioDoGrupo(p.diaOperacional()), TreeMap::new, Collectors.toList()));

        List<PontoSerie> serie = new ArrayList<>();
        for (LocalDate grupo = agrupamento.inicioDoGrupo(inicio); !grupo.isAfter(fim); grupo = agrupamento.fimDoGrupo(grupo).plusDays(1)) {
            LocalDate de = grupo.isBefore(inicio) ? inicio : grupo;
            LocalDate fimGrupo = agrupamento.fimDoGrupo(grupo);
            LocalDate ate = fimGrupo.isAfter(fim) ? fim : fimGrupo;
            MetricasCalculator.Totais t = calculadora.totais(porGrupo.getOrDefault(grupo, List.of()));
            serie.add(new PontoSerie(de, ate, t.faturamento(), t.pedidosConcluidos(), t.ticketMedio(), t.cancelados()));
        }
        return serie;
    }

    private static List<Fatia> fatias(List<MetricasCalculator.Fatia<String>> fatias, BigDecimal faturamentoTotal) {
        return fatias.stream()
                .map(f -> new Fatia(f.chave(), f.pedidos(), f.faturamento(), MetricasCalculator.percentual(f.faturamento(), faturamentoTotal)))
                .toList();
    }

    private static List<Faixa> faixas(List<MetricasCalculator.Fatia<Integer>> fatias) {
        return fatias.stream().map(f -> new Faixa(f.chave(), f.pedidos(), f.faturamento())).toList();
    }
}
