package com.nexusfood.analytics.repository;

import com.nexusfood.analytics.model.NexusInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface NexusInsightRepository extends JpaRepository<NexusInsight, Long> {

    List<NexusInsight> findAllByRestauranteIdAndDiaBetweenOrderByDiaDescIdDesc(Long restauranteId, LocalDate inicio, LocalDate fim);

    @Modifying
    @Query("DELETE FROM NexusInsight i WHERE i.restauranteId = :restauranteId AND i.dia = :dia")
    void apagarDia(@Param("restauranteId") Long restauranteId, @Param("dia") LocalDate dia);
}
