package com.nexusfood.catalogo.repository;

import com.nexusfood.catalogo.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findAllByRestauranteIdOrderByOrdemAscNomeAsc(Long restauranteId);
    List<Categoria> findAllByRestauranteIdAndAtivaTrueOrderByOrdemAscNomeAsc(Long restauranteId);
    Optional<Categoria> findByIdAndRestauranteId(Long id, Long restauranteId);
}
