package com.nexusfood.plataforma.controller;

import com.nexusfood.plataforma.dto.AssinaturaResponse;
import com.nexusfood.plataforma.dto.CheckoutResponse;
import com.nexusfood.plataforma.dto.PlanoDisponivelResponse;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.service.AssinaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assinatura")
@RequiredArgsConstructor
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping("/planos")
    public List<PlanoDisponivelResponse> planos() {
        return assinaturaService.listarPlanos();
    }

    @GetMapping
    public AssinaturaResponse status() {
        return assinaturaService.status(SecurityUtils.restauranteAtualId());
    }

    /** Cria a sessão de checkout na Stripe e devolve a URL para onde o navegador deve ser redirecionado. */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CheckoutResponse checkout(@RequestParam PlanoSaas plano) {
        String url = assinaturaService.criarSessaoCheckout(SecurityUtils.restauranteAtualId(), plano);
        return new CheckoutResponse(url);
    }

    /** URL do portal da Stripe para o restaurante gerenciar forma de pagamento ou cancelar. */
    @PostMapping("/portal")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CheckoutResponse portal() {
        String url = assinaturaService.criarSessaoPortal(SecurityUtils.restauranteAtualId());
        return new CheckoutResponse(url);
    }
}
