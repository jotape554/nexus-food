package com.nexusfood.plataforma.controller;

import com.nexusfood.plataforma.acesso.AcessoLivre;
import com.nexusfood.plataforma.dto.BairroEntregaRequest;
import com.nexusfood.plataforma.dto.ConfiguracaoRestauranteRequest;
import com.nexusfood.plataforma.dto.ConfiguracaoRestauranteResponse;
import com.nexusfood.plataforma.model.BairroEntrega;
import com.nexusfood.plataforma.service.RestauranteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AcessoLivre("Configurações da loja (abrir, fechar, entrega) valem em qualquer plano.")
@RequestMapping("/api/restaurante")
@RequiredArgsConstructor
public class RestauranteController {

    private final RestauranteService restauranteService;

    @GetMapping
    public ConfiguracaoRestauranteResponse configuracao() {
        return ConfiguracaoRestauranteResponse.de(restauranteService.atual());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ConfiguracaoRestauranteResponse atualizar(@Valid @RequestBody ConfiguracaoRestauranteRequest req) {
        return ConfiguracaoRestauranteResponse.de(restauranteService.atualizarConfiguracao(req));
    }

    /** Abre/fecha a loja. Liberado para toda a equipe: quem está no balcão é quem abre e fecha. */
    @PatchMapping("/aceitando-pedidos")
    public ConfiguracaoRestauranteResponse aceitandoPedidos(@RequestParam boolean valor) {
        return ConfiguracaoRestauranteResponse.de(restauranteService.definirAceitandoPedidos(valor));
    }

    @GetMapping("/bairros")
    public List<BairroEntrega> bairros() {
        return restauranteService.listarBairros();
    }

    @PostMapping("/bairros")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public BairroEntrega criarBairro(@Valid @RequestBody BairroEntregaRequest req) {
        return restauranteService.criarBairro(req);
    }

    @PutMapping("/bairros/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public BairroEntrega atualizarBairro(@PathVariable Long id, @Valid @RequestBody BairroEntregaRequest req) {
        return restauranteService.atualizarBairro(id, req);
    }

    @DeleteMapping("/bairros/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> excluirBairro(@PathVariable Long id) {
        restauranteService.excluirBairro(id);
        return ResponseEntity.noContent().build();
    }
}
