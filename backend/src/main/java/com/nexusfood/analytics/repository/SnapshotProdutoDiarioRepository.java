package com.nexusfood.analytics.repository;

import com.nexusfood.analytics.model.SnapshotProdutoDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SnapshotProdutoDiarioRepository extends JpaRepository<SnapshotProdutoDiario, Long> {

    /** Vendas por produto numa janela: [produtoId, quantidade, faturamento]. */
    @Query("""
            SELECT s.produtoId, SUM(s.quantidade), SUM(s.faturamento) FROM SnapshotProdutoDiario s
            WHERE s.restauranteId = :restauranteId AND s.dia BETWEEN :inicio AND :fim
            GROUP BY s.produtoId
            """)
    List<Object[]> vendasPorProduto(@Param("restauranteId") Long restauranteId, @Param("inicio") LocalDate inicio,
                                    @Param("fim") LocalDate fim);

    @Modifying
    @Query("DELETE FROM SnapshotProdutoDiario s WHERE s.restauranteId = :restauranteId AND s.dia = :dia")
    void apagarDia(@Param("restauranteId") Long restauranteId, @Param("dia") LocalDate dia);
}
