package com.nexusfood.analytics.controller;

import com.nexusfood.plataforma.acesso.RequerRecurso;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.analytics.dto.NexusResponse;
import com.nexusfood.analytics.service.NexusService;
import com.nexusfood.plataforma.acesso.AcessoPlanoService;
import com.nexusfood.plataforma.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nexus Analytics (nota, indicadores e insights). Assunto da gestão. */
@RestController
@RequerRecurso(Recurso.NEXUS_SCORE)
@RequestMapping("/api/nexus")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class NexusController {

    private final NexusService nexusService;
    private final AcessoPlanoService acessoPlano;

    /** A nota é do Profissional; o detalhe de cada indicador e as dicas, do Premium. */
    @GetMapping
    public NexusResponse painel() {
        Long restauranteId = SecurityUtils.restauranteAtualId();
        NexusResponse completo = nexusService.painel(restauranteId);
        return acessoPlano.liberado(restauranteId, Recurso.NEXUS_DETALHES) ? completo : completo.semDetalhes();
    }
}
