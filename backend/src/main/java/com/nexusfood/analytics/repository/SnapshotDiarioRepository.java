package com.nexusfood.analytics.repository;

import com.nexusfood.analytics.model.SnapshotDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SnapshotDiarioRepository extends JpaRepository<SnapshotDiario, Long> {

    List<SnapshotDiario> findAllByRestauranteIdAndDiaBetweenOrderByDia(Long restauranteId, LocalDate inicio, LocalDate fim);

    @Query("SELECT s.dia FROM SnapshotDiario s WHERE s.restauranteId = :restauranteId AND s.dia BETWEEN :inicio AND :fim AND s.versaoCalculo = :versao")
    List<LocalDate> diasComSnapshot(@Param("restauranteId") Long restauranteId, @Param("inicio") LocalDate inicio,
                                    @Param("fim") LocalDate fim, @Param("versao") int versao);

    @Modifying
    @Query("DELETE FROM SnapshotDiario s WHERE s.restauranteId = :restauranteId AND s.dia = :dia")
    void apagarDia(@Param("restauranteId") Long restauranteId, @Param("dia") LocalDate dia);
}
