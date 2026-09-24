package com.nexusfood.plataforma.controller;

import com.nexusfood.plataforma.acesso.AcessoLivre;
import com.nexusfood.plataforma.dto.AssinaturaResponse;
import com.nexusfood.plataforma.dto.CheckoutResponse;
import com.nexusfood.plataforma.dto.MudancaPlanoResponse;
import com.nexusfood.plataforma.dto.PlanoDisponivelResponse;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.security.UsuarioPrincipal;
import com.nexusfood.plataforma.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assinatura")
@RequiredArgsConstructor
@AcessoLivre("É daqui que o restaurante escolhe o plano: precisa abrir em qualquer situação.")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping("/planos")
    public List<PlanoDisponivelResponse> planos() {
        return assinaturaService.listarPlanos();
    }

    @GetMapping
    public AssinaturaResponse status() {
        UsuarioPrincipal usuario = SecurityUtils.usuarioAtual();
        return assinaturaService.status(usuario.getRestauranteId(), usuario.getId());
    }

    /** Assina (checkout da Stripe) ou troca o plano da assinatura que já existe. */
    @PostMapping("/plano")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MudancaPlanoResponse escolherPlano(@RequestParam PlanoSaas plano) {
        return assinaturaService.escolherPlano(SecurityUtils.restauranteAtualId(), plano);
    }

    /** URL do portal da Stripe para o restaurante gerenciar forma de pagamento ou cancelar. */
    @PostMapping("/portal")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CheckoutResponse portal() {
        return new CheckoutResponse(assinaturaService.criarSessaoPortal(SecurityUtils.restauranteAtualId()));
    }
}
