package com.nexusfood.catalogo.controller;

import com.nexusfood.plataforma.acesso.RequerRecurso;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.catalogo.dto.CategoriaRequest;
import com.nexusfood.catalogo.model.Categoria;
import com.nexusfood.catalogo.service.CatalogoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequerRecurso(Recurso.CARDAPIO)
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CatalogoService catalogoService;

    @GetMapping
    public List<Categoria> listar() {
        return catalogoService.listarCategorias();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public Categoria criar(@Valid @RequestBody CategoriaRequest req) {
        return catalogoService.criarCategoria(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public Categoria atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return catalogoService.atualizarCategoria(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        catalogoService.excluirCategoria(id);
        return ResponseEntity.noContent().build();
    }
}
