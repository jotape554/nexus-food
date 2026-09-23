package com.nexusfood.catalogo.repository;

import com.nexusfood.catalogo.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findAllByRestauranteIdAndAtivoTrueOrderByOrdemAscNomeAsc(Long restauranteId);
    Optional<Produto> findByIdAndRestauranteId(Long id, Long restauranteId);
    List<Produto> findAllByIdInAndRestauranteId(Collection<Long> ids, Long restauranteId);
    boolean existsByCategoriaIdAndAtivoTrue(Long categoriaId);
}
