package com.nexusfood.clientes.service;

import com.nexusfood.clientes.model.Cliente;
import com.nexusfood.clientes.repository.ClienteRepository;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.util.TelefoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final RestauranteRepository restauranteRepository;

    /**
     * Localiza o cliente pelo telefone (normalizado) ou cria um novo. O nome é atualizado para
     * o último informado — o cliente pode ter corrigido um erro de digitação.
     *
     * Chamado só na criação de pedido, que já trava a linha do restaurante: dois pedidos
     * simultâneos do mesmo número não conseguem criar o cliente em dobro. A constraint única
     * (restaurante_id, telefone) continua lá como última garantia.
     */
    @Transactional
    public Cliente identificar(Long restauranteId, String telefone, String nome, Instant agora) {
        String normalizado = TelefoneUtil.normalizar(telefone);
        if (normalizado == null) {
            throw new RegraDeNegocioException("Informe um telefone válido com DDD.");
        }
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Informe seu nome.");
        }
        String nomeLimpo = nome.trim();

        return clienteRepository.findByRestauranteIdAndTelefone(restauranteId, normalizado)
                .map(existente -> {
                    existente.setNome(nomeLimpo);
                    return existente;
                })
                .orElseGet(() -> clienteRepository.save(Cliente.builder()
                        .restaurante(restauranteRepository.getReferenceById(restauranteId))
                        .telefone(normalizado)
                        .nome(nomeLimpo)
                        .criadoEm(agora)
                        .build()));
    }

    public Page<Cliente> listar(String busca, Pageable pageable) {
        Long restauranteId = SecurityUtils.restauranteAtualId();
        if (busca == null || busca.isBlank()) {
            return clienteRepository.findAllByRestauranteIdOrderByNomeAsc(restauranteId, pageable);
        }
        String termo = busca.trim();
        String digitos = termo.replaceAll("\\D", "");
        if (!digitos.isEmpty() && digitos.length() == termo.replaceAll("[\\s()+-]", "").length()) {
            termo = digitos; // busca por telefone: ignora máscara
        }
        return clienteRepository.buscar(restauranteId, termo, pageable);
    }
}
