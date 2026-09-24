package com.nexusfood.analytics.service;

import com.nexusfood.analytics.model.SnapshotDiario;
import com.nexusfood.analytics.repository.AnalyticsRepository;
import com.nexusfood.analytics.repository.SnapshotDiarioRepository;
import com.nexusfood.analytics.repository.SnapshotProdutoDiarioRepository;
import com.nexusfood.analytics.service.NexusScoreEngine.Medida;
import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.relatorios.model.PedidoResumo;
import com.nexusfood.relatorios.repository.RelatorioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Mede os indicadores do Nexus Score para um dia de referência D.
 *
 * Janela atual J = os 28 dias operacionais que terminam em D; janela anterior J-1 = os 28 antes.
 * (28 = 4 semanas completas: cada janela tem o mesmo número de sextas e sábados.)
 *
 * Somas (faturamento, pedidos, cancelamentos, pontualidade, vendas por produto) vêm dos resumos
 * diários; o que não pode ser somado dia a dia (clientes distintos, mediana do tempo de aceite)
 * vem direto dos pedidos. Valores em %, minutos ou coeficiente, conforme a unidade da regra.
 */
@Component
@RequiredArgsConstructor
public class ColetorIndicadores {

    public static final int JANELA = 28;

    private final SnapshotDiarioRepository snapshotRepository;
    private final SnapshotProdutoDiarioRepository snapshotProdutoRepository;
    private final RelatorioRepository relatorioRepository;
    private final AnalyticsRepository analyticsRepository;

    /** O que o score e os insights precisam saber sobre o período. */
    public record Coleta(
            LocalDate dia,
            LocalDate inicioJanela,
            NexusScoreEngine.Entrada entrada,
            BigDecimal faturamentoJanela,
            BigDecimal faturamentoAnterior,
            long[] pedidosPorHora,
            Map<Long, Long> vendidosJanela,
            Map<Long, Long> vendidosAnterior,
            Map<Long, String> nomesProdutos,
            List<String> produtosParados,
            MotivoCancelamento motivoCancelamentoMaisComum
    ) {
        public Double valor(String codigo) {
            Medida m = entrada.medidas().get(codigo);
            return m == null ? null : m.valor();
        }
    }

    public Coleta coletar(Long restauranteId, LocalDate dia, ZoneId fuso) {
        LocalDate inicioJ = dia.minusDays(JANELA - 1L);
        LocalDate fimJ1 = inicioJ.minusDays(1);
        LocalDate inicioJ1 = inicioJ.minusDays(JANELA);

        LocalDate primeiroDia = analyticsRepository.primeiroDiaComPedido(restauranteId);
        int diasHistorico = primeiroDia == null || primeiroDia.isAfter(dia) ? 0
                : (int) ChronoUnit.DAYS.between(primeiroDia, dia) + 1;

        List<SnapshotDiario> snaps = snapshotRepository.findAllByRestauranteIdAndDiaBetweenOrderByDia(restauranteId, inicioJ1, dia);
        List<SnapshotDiario> sJ = snaps.stream().filter(s -> !s.getDia().isBefore(inicioJ)).toList();
        List<SnapshotDiario> sJ1 = snaps.stream().filter(s -> s.getDia().isBefore(inicioJ)).toList();

        BigDecimal fatJ = somaDinheiro(sJ);
        BigDecimal fatJ1 = somaDinheiro(sJ1);
        long conclJ = soma(sJ, SnapshotDiario::getPedidosConcluidos);
        long conclJ1 = soma(sJ1, SnapshotDiario::getPedidosConcluidos);
        long recebJ = soma(sJ, SnapshotDiario::getPedidosRecebidos);

        List<PedidoResumo> pedidos = relatorioRepository.pedidosDoPeriodo(restauranteId, inicioJ1, dia);
        List<PedidoResumo> pJ = pedidos.stream().filter(p -> !p.diaOperacional().isBefore(inicioJ)).toList();
        List<PedidoResumo> concluidos = pedidos.stream().filter(PedidoResumo::concluido).toList();

        Map<String, Medida> medidas = new HashMap<>();

        // ---------- Vendas ----------
        long amostraComparacao = Math.min(conclJ, conclJ1);
        medidas.put("V1_CRESCIMENTO_FATURAMENTO", new Medida(variacao(fatJ.doubleValue(), fatJ1.doubleValue()), amostraComparacao, null));
        Double ticketJ = conclJ == 0 ? null : fatJ.doubleValue() / conclJ;
        Double ticketJ1 = conclJ1 == 0 ? null : fatJ1.doubleValue() / conclJ1;
        medidas.put("V2_EVOLUCAO_TICKET", new Medida(ticketJ == null || ticketJ1 == null ? null : variacao(ticketJ, ticketJ1),
                amostraComparacao, null));
        medidas.put("V3_REGULARIDADE", new Medida(coeficienteSemanal(sJ, inicioJ), conclJ, null));

        // ---------- Clientes ----------
        Map<Long, LocalDate> primeiraCompra = new HashMap<>();
        for (Object[] linha : analyticsRepository.primeirasCompras(restauranteId, dia, StatusPedido.CONCLUIDO)) {
            primeiraCompra.put((Long) linha[0], (LocalDate) linha[1]);
        }
        Set<Long> clientesJ = clientes(concluidos, p -> !p.diaOperacional().isBefore(inicioJ));
        Set<Long> clientesJ1 = clientes(concluidos, p -> p.diaOperacional().isBefore(inicioJ));
        long recorrentes = clientesJ.stream().filter(c -> primeiraCompra.get(c).isBefore(inicioJ)).count();
        medidas.put("C1_RECOMPRA", new Medida(percentual(recorrentes, clientesJ.size()), clientesJ.size(), null));

        List<Long> novosJ1 = primeiraCompra.entrySet().stream()
                .filter(e -> !e.getValue().isBefore(inicioJ1) && !e.getValue().isAfter(fimJ1))
                .map(Map.Entry::getKey).toList();
        Map<Long, List<LocalDate>> diasPorCliente = concluidos.stream()
                .collect(Collectors.groupingBy(PedidoResumo::clienteId, Collectors.mapping(PedidoResumo::diaOperacional, Collectors.toList())));
        long voltaram = novosJ1.stream().filter(c -> {
            LocalDate primeira = primeiraCompra.get(c);
            long compras = diasPorCliente.getOrDefault(c, List.of()).stream()
                    .filter(d -> !d.isBefore(primeira) && !d.isAfter(primeira.plusDays(JANELA))).count();
            return compras >= 2;
        }).count();
        medidas.put("C2_RETENCAO_NOVOS", new Medida(percentual(voltaram, novosJ1.size()), novosJ1.size(), null));
        medidas.put("C3_BASE_ATIVA", new Medida(variacao(clientesJ.size(), clientesJ1.size()), amostraComparacao, null));

        // ---------- Operação ----------
        long canceladosRestaurante = soma(sJ, SnapshotDiario::getCanceladosRestaurante);
        medidas.put("O1_CANCELAMENTO_RESTAURANTE", new Medida(percentual(canceladosRestaurante, recebJ), recebJ, null));

        List<Double> aceites = pJ.stream().filter(p -> p.confirmadoEm() != null)
                .map(p -> Duration.between(p.criadoEm(), p.confirmadoEm()).toSeconds() / 60.0).sorted().toList();
        long concluidosComAceite = pJ.stream().filter(p -> p.concluido() && p.confirmadoEm() != null).count();
        medidas.put("O2_TEMPO_ACEITE", new Medida(mediana(aceites), aceites.size(), fracao(concluidosComAceite, conclJ)));

        long comPronto = soma(sJ, SnapshotDiario::getPedidosComPronto);
        long noPrazo = soma(sJ, SnapshotDiario::getProntosNoPrazo);
        medidas.put("O3_PONTUALIDADE", new Medida(percentual(noPrazo, comPronto), comPronto, fracao(comPronto, conclJ)));

        // ---------- Cardápio ----------
        Map<Long, Long> vendidosJ = vendas(restauranteId, inicioJ, dia);
        Map<Long, Long> vendidosJ1 = vendas(restauranteId, inicioJ1, fimJ1);
        List<Object[]> antigos = analyticsRepository.produtosAtivosDesde(restauranteId, inicioJ.atStartOfDay(fuso).toInstant());
        List<String> parados = antigos.stream().filter(l -> vendidosJ.getOrDefault((Long) l[0], 0L) == 0)
                .map(l -> (String) l[1]).toList();
        medidas.put("K1_PRODUTOS_PARADOS", new Medida(percentual(parados.size(), antigos.size()), antigos.size(), null));

        Map<Long, String> nomes = new HashMap<>();
        analyticsRepository.nomesDosProdutos(restauranteId).forEach(l -> nomes.put((Long) l[0], (String) l[1]));

        MotivoCancelamento motivoMaisComum = pJ.stream()
                .filter(p -> p.cancelado() && p.canceladoPor() == CanceladoPor.RESTAURANTE && p.motivoCancelamento() != null)
                .collect(Collectors.groupingBy(PedidoResumo::motivoCancelamento, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        return new Coleta(dia, inicioJ, new NexusScoreEngine.Entrada(diasHistorico, conclJ, medidas), fatJ, fatJ1,
                somaPorHora(sJ), vendidosJ, vendidosJ1, nomes, parados, motivoMaisComum);
    }

    // ---------- contas ----------

    private Map<Long, Long> vendas(Long restauranteId, LocalDate inicio, LocalDate fim) {
        Map<Long, Long> vendas = new HashMap<>();
        for (Object[] l : snapshotProdutoRepository.vendasPorProduto(restauranteId, inicio, fim)) {
            vendas.put((Long) l[0], ((Number) l[1]).longValue());
        }
        return vendas;
    }

    /** Desvio padrão ÷ média do faturamento das 4 semanas da janela. */
    private static Double coeficienteSemanal(List<SnapshotDiario> sJ, LocalDate inicioJ) {
        double[] semanas = new double[4];
        for (SnapshotDiario s : sJ) {
            int semana = (int) (ChronoUnit.DAYS.between(inicioJ, s.getDia()) / 7);
            if (semana >= 0 && semana < 4) semanas[semana] += s.getFaturamento().doubleValue();
        }
        double media = Arrays.stream(semanas).average().orElse(0);
        if (media == 0) return null;
        double variancia = Arrays.stream(semanas).map(v -> (v - media) * (v - media)).sum() / semanas.length;
        return Math.sqrt(variancia) / media;
    }

    private static long[] somaPorHora(List<SnapshotDiario> sJ) {
        long[] horas = new long[24];
        for (SnapshotDiario s : sJ) {
            String[] partes = s.getPedidosPorHora().split(",");
            for (int h = 0; h < 24 && h < partes.length; h++) horas[h] += Long.parseLong(partes[h].trim());
        }
        return horas;
    }

    private static Set<Long> clientes(List<PedidoResumo> concluidos, Predicate<PedidoResumo> filtro) {
        return concluidos.stream().filter(filtro).map(PedidoResumo::clienteId).collect(Collectors.toSet());
    }

    private static long soma(List<SnapshotDiario> lista, java.util.function.ToIntFunction<SnapshotDiario> campo) {
        return lista.stream().mapToLong(campo::applyAsInt).sum();
    }

    private static BigDecimal somaDinheiro(List<SnapshotDiario> lista) {
        return lista.stream().map(SnapshotDiario::getFaturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double variacao(double atual, double anterior) {
        return anterior == 0 ? null : (atual / anterior - 1) * 100;
    }

    private static Double percentual(long parte, long todo) {
        return todo == 0 ? null : parte * 100.0 / todo;
    }

    private static Double fracao(long parte, long todo) {
        return todo == 0 ? null : (double) parte / todo;
    }

    private static Double mediana(List<Double> ordenados) {
        if (ordenados.isEmpty()) return null;
        int n = ordenados.size();
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
    }
}
