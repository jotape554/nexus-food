package com.nexusfood.relatorios.controller;

import com.nexusfood.relatorios.dto.RelatorioVendasResponse;
import com.nexusfood.relatorios.model.Agrupamento;
import com.nexusfood.relatorios.service.RelatorioVendasService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Relatórios de vendas. Faturamento é assunto da gestão: atendente não acessa. */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class RelatorioController {

    private final RelatorioVendasService relatorioVendasService;

    /** Sem parâmetros: últimos 30 dias, por dia. */
    @GetMapping("/vendas")
    public RelatorioVendasResponse vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) Agrupamento agrupamento) {
        return relatorioVendasService.gerar(inicio, fim, agrupamento);
    }
}
