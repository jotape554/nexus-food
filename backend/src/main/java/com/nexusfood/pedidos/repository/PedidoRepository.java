package com.nexusfood.pedidos.repository;

import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByIdAndRestauranteId(Long id, Long restauranteId);

    Optional<Pedido> findByCodigoPublico(String codigoPublico);

    Optional<Pedido> findByRestauranteIdAndChaveIdempotencia(Long restauranteId, String chaveIdempotencia);

    @Query("SELECT COALESCE(MAX(p.numeroDia), 0) FROM Pedido p WHERE p.restaurante.id = :restauranteId AND p.diaOperacional = :dia")
    int maiorNumeroDoDia(@Param("restauranteId") Long restauranteId, @Param("dia") LocalDate dia);

    @Query("""
            SELECT p FROM Pedido p JOIN FETCH p.cliente
            WHERE p.restaurante.id = :restauranteId AND p.status IN :status
            ORDER BY p.criadoEm ASC
            """)
    List<Pedido> findAllComStatus(@Param("restauranteId") Long restauranteId, @Param("status") Collection<StatusPedido> status);

    @Query("""
            SELECT p FROM Pedido p JOIN FETCH p.cliente
            WHERE p.restaurante.id = :restauranteId AND p.diaOperacional = :dia
            ORDER BY p.numeroDia DESC
            """)
    List<Pedido> findAllDoDia(@Param("restauranteId") Long restauranteId, @Param("dia") LocalDate dia);
}
