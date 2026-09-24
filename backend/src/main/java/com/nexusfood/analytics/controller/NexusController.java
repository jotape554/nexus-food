package com.nexusfood.analytics.controller;

import com.nexusfood.analytics.dto.NexusResponse;
import com.nexusfood.analytics.service.NexusService;
import com.nexusfood.plataforma.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nexus Analytics (nota, indicadores e insights). Assunto da gestão. */
@RestController
@RequestMapping("/api/nexus")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class NexusController {

    private final NexusService nexusService;

    @GetMapping
    public NexusResponse painel() {
        return nexusService.painel(SecurityUtils.restauranteAtualId());
    }
}
