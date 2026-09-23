package com.nexusfood.plataforma.repository;

import com.nexusfood.plataforma.model.BairroEntrega;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BairroEntregaRepository extends JpaRepository<BairroEntrega, Long> {
    List<BairroEntrega> findAllByRestauranteIdOrderByNomeAsc(Long restauranteId);
    List<BairroEntrega> findAllByRestauranteIdAndAtivoTrueOrderByNomeAsc(Long restauranteId);
    Optional<BairroEntrega> findByIdAndRestauranteId(Long id, Long restauranteId);
}
