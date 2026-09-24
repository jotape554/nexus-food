package com.nexusfood.analytics.repository;

import com.nexusfood.analytics.model.NexusScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NexusScoreRepository extends JpaRepository<NexusScore, Long> {

    Optional<NexusScore> findByRestauranteIdAndDiaAndRegraVersao(Long restauranteId, LocalDate dia, String regraVersao);

    List<NexusScore> findAllByRestauranteIdAndRegraVersaoAndDiaBetweenOrderByDia(Long restauranteId, String regraVersao,
                                                                                LocalDate inicio, LocalDate fim);

    boolean existsByRestauranteIdAndDiaAndRegraVersao(Long restauranteId, LocalDate dia, String regraVersao);
}
