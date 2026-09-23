package com.nexusfood.catalogo.controller;

import com.nexusfood.catalogo.dto.ProdutoRequest;
import com.nexusfood.catalogo.dto.ProdutoResponse;
import com.nexusfood.catalogo.service.CatalogoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final CatalogoService catalogoService;

    @GetMapping
    public List<ProdutoResponse> listar() {
        return catalogoService.listarProdutos();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ProdutoResponse criar(@Valid @RequestBody ProdutoRequest req) {
        return catalogoService.criarProduto(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ProdutoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequest req) {
        return catalogoService.atualizarProduto(id, req);
    }

    /** Marcar como esgotado/disponível: liberado para toda a equipe. */
    @PatchMapping("/{id}/disponivel")
    public ProdutoResponse disponivel(@PathVariable Long id, @RequestParam boolean valor) {
        return catalogoService.definirDisponivel(id, valor);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        catalogoService.removerProduto(id);
        return ResponseEntity.noContent().build();
    }
}
