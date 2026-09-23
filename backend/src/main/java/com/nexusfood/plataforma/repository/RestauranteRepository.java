package com.nexusfood.plataforma.repository;

import com.nexusfood.plataforma.model.Restaurante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestauranteRepository extends JpaRepository<Restaurante, Long> {
    Optional<Restaurante> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Restaurante> findByStripeCustomerId(String stripeCustomerId);
    Optional<Restaurante> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Trava a linha do restaurante até o fim da transação. Usado na criação de pedido para
     * serializar a numeração do dia (#1, #2...) e a checagem de idempotência.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Restaurante r WHERE r.id = :id")
    Optional<Restaurante> travarPorId(@Param("id") Long id);
}
