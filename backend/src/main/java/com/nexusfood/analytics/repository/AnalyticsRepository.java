package com.nexusfood.analytics.repository;

import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Leituras de pedidos/produtos que o Nexus precisa além do que os relatórios já oferecem. Só leitura. */
public interface AnalyticsRepository extends Repository<Pedido, Long> {

    @Query("SELECT MIN(p.diaOperacional) FROM Pedido p WHERE p.restaurante.id = :restauranteId")
    LocalDate primeiroDiaComPedido(@Param("restauranteId") Long restauranteId);

    /** [clienteId, dia da primeira compra concluída], considerando só a história até :ate. */
    @Query("""
            SELECT p.cliente.id, MIN(p.diaOperacional) FROM Pedido p
            WHERE p.restaurante.id = :restauranteId AND p.status = :concluido AND p.diaOperacional <= :ate
            GROUP BY p.cliente.id
            """)
    List<Object[]> primeirasCompras(@Param("restauranteId") Long restauranteId, @Param("ate") LocalDate ate,
                                    @Param("concluido") StatusPedido concluido);

    /** [produtoId, nome] dos produtos ativos que já estavam no cardápio em :desde. */
    @Query("""
            SELECT pr.id, pr.nome FROM Produto pr
            WHERE pr.restaurante.id = :restauranteId AND pr.ativo = true AND pr.criadoEm <= :desde
            ORDER BY pr.nome
            """)
    List<Object[]> produtosAtivosDesde(@Param("restauranteId") Long restauranteId, @Param("desde") Instant desde);

    @Query("SELECT pr.id, pr.nome FROM Produto pr WHERE pr.restaurante.id = :restauranteId")
    List<Object[]> nomesDosProdutos(@Param("restauranteId") Long restauranteId);
}
