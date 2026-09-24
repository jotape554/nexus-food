package com.nexusfood.relatorios.service;

import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.relatorios.model.PedidoResumo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Único lugar onde as métricas de venda são calculadas. Relatórios (Fase 3) e snapshots do
 * Nexus (Fase 4) usam esta mesma classe, então um número nunca diverge entre as duas telas.
 *
 * Definições (valem para o sistema inteiro):
 * - venda = pedido CONCLUIDO; faturamento = soma do total (itens + entrega) dos concluídos;
 * - pedidos recebidos = todos os pedidos do período, em qualquer status;
 * - ticket médio = faturamento ÷ pedidos concluídos;
 * - taxa de cancelamento = cancelados ÷ recebidos;
 * - distribuições (modalidade, pagamento, hora, dia da semana) contam só as vendas.
 * Sem banco e sem relógio: recebe os pedidos e devolve números.
 */
@Component
public class MetricasCalculator {

    public record Totais(
            BigDecimal faturamento,
            long pedidosConcluidos,
            long pedidosRecebidos,
            BigDecimal ticketMedio,
            long cancelados,
            long canceladosPeloRestaurante,
            BigDecimal taxaCancelamento,
            long clientesUnicos
    ) {}

    public record Fatia<K>(K chave, long pedidos, BigDecimal faturamento) {}

    public Totais totais(Collection<PedidoResumo> pedidos) {
        BigDecimal faturamento = BigDecimal.ZERO;
        long concluidos = 0;
        long cancelados = 0;
        long canceladosRestaurante = 0;
        Set<Long> clientes = new HashSet<>();

        for (PedidoResumo p : pedidos) {
            if (p.concluido()) {
                faturamento = faturamento.add(p.total());
                concluidos++;
                clientes.add(p.clienteId());
            } else if (p.cancelado()) {
                cancelados++;
                if (p.canceladoPor() == CanceladoPor.RESTAURANTE) canceladosRestaurante++;
            }
        }

        return new Totais(
                dinheiro(faturamento),
                concluidos,
                pedidos.size(),
                concluidos == 0 ? BigDecimal.ZERO : faturamento.divide(BigDecimal.valueOf(concluidos), 2, RoundingMode.HALF_UP),
                cancelados,
                canceladosRestaurante,
                percentual(cancelados, pedidos.size()),
                clientes.size());
    }

    /** Vendas agrupadas por uma chave (modalidade, forma de pagamento...), da maior para a menor em R$. */
    public <K> List<Fatia<K>> porChave(Collection<PedidoResumo> pedidos, Function<PedidoResumo, K> chave) {
        Map<K, long[]> quantidade = new LinkedHashMap<>();
        Map<K, BigDecimal> valor = new LinkedHashMap<>();
        for (PedidoResumo p : pedidos) {
            if (!p.concluido()) continue;
            K k = chave.apply(p);
            quantidade.computeIfAbsent(k, x -> new long[1])[0]++;
            valor.merge(k, p.total(), BigDecimal::add);
        }
        List<Fatia<K>> fatias = new ArrayList<>();
        quantidade.forEach((k, q) -> fatias.add(new Fatia<>(k, q[0], dinheiro(valor.get(k)))));
        fatias.sort((a, b) -> b.faturamento().compareTo(a.faturamento()));
        return fatias;
    }

    /** Vendas por hora do dia (0–23) no fuso do restaurante, sempre com as 24 horas. */
    public List<Fatia<Integer>> porHora(Collection<PedidoResumo> pedidos, ZoneId fuso) {
        return completar(porChave(pedidos, p -> p.criadoEm().atZone(fuso).getHour()), 0, 23);
    }

    /** Vendas por dia da semana (1 = segunda ... 7 = domingo) do dia operacional, sempre com os 7 dias. */
    public List<Fatia<Integer>> porDiaDaSemana(Collection<PedidoResumo> pedidos) {
        return completar(porChave(pedidos, p -> p.diaOperacional().getDayOfWeek().getValue()), 1, 7);
    }

    private List<Fatia<Integer>> completar(List<Fatia<Integer>> fatias, int de, int ate) {
        Map<Integer, Fatia<Integer>> porChave = new java.util.HashMap<>();
        fatias.forEach(f -> porChave.put(f.chave(), f));
        List<Fatia<Integer>> completo = new ArrayList<>();
        for (int i = de; i <= ate; i++) {
            completo.add(porChave.getOrDefault(i, new Fatia<>(i, 0, dinheiro(BigDecimal.ZERO))));
        }
        return completo;
    }

    /** parte ÷ todo em %, com uma casa. Zero quando não há base. */
    public static BigDecimal percentual(long parte, long todo) {
        if (todo == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(parte * 100L).divide(BigDecimal.valueOf(todo), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (todo.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return parte.multiply(BigDecimal.valueOf(100)).divide(todo, 1, RoundingMode.HALF_UP);
    }

    /** Variação % de "atual" contra "anterior"; null quando não existe base para comparar. */
    public static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) return null;
        return atual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
