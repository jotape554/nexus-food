package com.nexusfood.relatorios.repository;

import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.Pedido;
import com.nexusfood.relatorios.model.PedidoResumo;
import com.nexusfood.relatorios.model.ProdutoVendido;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Consultas de leitura dos relatórios. Nunca escreve em pedidos. */
public interface RelatorioRepository extends Repository<Pedido, Long> {

    @Query("""
            SELECT new com.nexusfood.relatorios.model.PedidoResumo(
                p.cliente.id, p.diaOperacional, p.criadoEm, p.status, p.canceladoPor, p.modalidade, p.formaPagamento, p.total)
            FROM Pedido p
            WHERE p.restaurante.id = :restauranteId AND p.diaOperacional BETWEEN :inicio AND :fim
            """)
    List<PedidoResumo> pedidosDoPeriodo(@Param("restauranteId") Long restauranteId,
                                        @Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim);

    /** Agregado no banco: soma por produto dos pedidos concluídos, do mais vendido (em R$) para o menos. */
    @Query("""
            SELECT new com.nexusfood.relatorios.model.ProdutoVendido(pr.id, pr.nome, SUM(i.quantidade), SUM(i.subtotal))
            FROM ItemPedido i JOIN i.pedido p JOIN i.produto pr
            WHERE p.restaurante.id = :restauranteId AND p.status = :concluido
              AND p.diaOperacional BETWEEN :inicio AND :fim
            GROUP BY pr.id, pr.nome
            ORDER BY SUM(i.subtotal) DESC, SUM(i.quantidade) DESC
            """)
    List<ProdutoVendido> produtosVendidos(@Param("restauranteId") Long restauranteId,
                                          @Param("inicio") LocalDate inicio,
                                          @Param("fim") LocalDate fim,
                                          @Param("concluido") StatusPedido concluido);

    /** Clientes cujo primeiro pedido concluído (em toda a história do restaurante) caiu no período. */
    @Query("""
            SELECT COUNT(DISTINCT p.cliente.id) FROM Pedido p
            WHERE p.restaurante.id = :restauranteId AND p.status = :concluido
              AND p.diaOperacional BETWEEN :inicio AND :fim
              AND NOT EXISTS (
                  SELECT 1 FROM Pedido q
                  WHERE q.restaurante.id = :restauranteId AND q.cliente.id = p.cliente.id
                    AND q.status = :concluido AND q.diaOperacional < :inicio)
            """)
    long clientesNovos(@Param("restauranteId") Long restauranteId,
                       @Param("inicio") LocalDate inicio,
                       @Param("fim") LocalDate fim,
                       @Param("concluido") StatusPedido concluido);
}
