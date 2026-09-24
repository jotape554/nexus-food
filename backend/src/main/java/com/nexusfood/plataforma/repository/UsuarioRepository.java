package com.nexusfood.plataforma.repository;

import com.nexusfood.plataforma.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByCpf(String cpf);
    Optional<Usuario> findByResetSenhaToken(String resetSenhaToken);

    Optional<Usuario> findByIdAndRestauranteId(Long id, Long restauranteId);

    List<Usuario> findAllByRestauranteIdOrderByAtivoDescNomeAsc(Long restauranteId);

    long countByRestauranteIdAndAtivoTrue(Long restauranteId);

    long countByRestauranteIdAndAtivoTrueAndPapel(Long restauranteId, com.nexusfood.plataforma.enums.Papel papel);

    /**
     * Usuários ativos na ordem em que "cabem" no plano: administradores primeiro, depois quem
     * entrou antes. Se o plano diminuir, quem passa do limite é sempre o mais recente.
     */
    @Query("""
            select u.id from Usuario u
            where u.restaurante.id = :restauranteId and u.ativo = true
            order by case when u.papel = com.nexusfood.plataforma.enums.Papel.ADMINISTRADOR then 0 else 1 end, u.id
            """)
    List<Long> idsAtivosPorPrioridade(@Param("restauranteId") Long restauranteId);
}
