package com.nexusfood.equipe.controller;

import com.nexusfood.equipe.dto.AtualizarUsuarioRequest;
import com.nexusfood.equipe.dto.ConviteResponse;
import com.nexusfood.equipe.dto.EquipeResponse;
import com.nexusfood.equipe.dto.UsuarioEquipeRequest;
import com.nexusfood.equipe.dto.UsuarioEquipeResponse;
import com.nexusfood.equipe.service.EquipeService;
import com.nexusfood.plataforma.acesso.RequerRecurso;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Equipe do restaurante. Só o administrador vê e mexe; o limite de usuários vem do plano. */
@RestController
@RequerRecurso(Recurso.EQUIPE)
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class EquipeController {

    private final EquipeService equipeService;

    @GetMapping
    public EquipeResponse listar() {
        UsuarioPrincipal eu = SecurityUtils.usuarioAtual();
        return equipeService.listar(eu.getRestauranteId(), eu.getId());
    }

    @PostMapping
    public ConviteResponse convidar(@Valid @RequestBody UsuarioEquipeRequest req) {
        UsuarioPrincipal eu = SecurityUtils.usuarioAtual();
        return equipeService.convidar(eu.getRestauranteId(), eu.getId(), req);
    }

    @PutMapping("/{id}")
    public UsuarioEquipeResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarUsuarioRequest req) {
        UsuarioPrincipal eu = SecurityUtils.usuarioAtual();
        return equipeService.atualizar(eu.getRestauranteId(), eu.getId(), id, req);
    }

    @PatchMapping("/{id}/ativo")
    public UsuarioEquipeResponse alterarAtivo(@PathVariable Long id, @RequestParam boolean valor) {
        UsuarioPrincipal eu = SecurityUtils.usuarioAtual();
        return equipeService.alterarAtivo(eu.getRestauranteId(), eu.getId(), id, valor);
    }

    @PostMapping("/{id}/link")
    public ConviteResponse novoLink(@PathVariable Long id) {
        UsuarioPrincipal eu = SecurityUtils.usuarioAtual();
        return equipeService.novoLink(eu.getRestauranteId(), eu.getId(), id);
    }
}
