package com.nexusfood.clientes.repository;

import com.nexusfood.clientes.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByRestauranteIdAndTelefone(Long restauranteId, String telefone);

    Page<Cliente> findAllByRestauranteIdOrderByNomeAsc(Long restauranteId, Pageable pageable);

    /**
     * Busca sempre com termo preenchido. (Um ":busca IS NULL OR ..." com parâmetro nulo quebra
     * no PostgreSQL — o driver manda o nulo como bytea e LOWER(bytea) não existe.)
     */
    @Query("""
            SELECT c FROM Cliente c
            WHERE c.restaurante.id = :restauranteId
              AND (LOWER(c.nome) LIKE LOWER(CONCAT('%', :busca, '%')) OR c.telefone LIKE CONCAT('%', :busca, '%'))
            ORDER BY c.nome
            """)
    Page<Cliente> buscar(@Param("restauranteId") Long restauranteId, @Param("busca") String busca, Pageable pageable);
}
