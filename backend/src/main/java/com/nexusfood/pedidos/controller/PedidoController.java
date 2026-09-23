package com.nexusfood.pedidos.controller;

import com.nexusfood.pedidos.dto.MudarStatusRequest;
import com.nexusfood.pedidos.dto.PedidoResponse;
import com.nexusfood.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Painel de pedidos do restaurante. Toda a equipe (inclusive atendente) pode operar. */
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @GetMapping("/em-andamento")
    public List<PedidoResponse> emAndamento() {
        return pedidoService.emAndamento();
    }

    @GetMapping
    public List<PedidoResponse> doDia(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return pedidoService.doDia(dia);
    }

    @GetMapping("/{id}")
    public PedidoResponse detalhe(@PathVariable Long id) {
        return pedidoService.detalhe(id);
    }

    @PatchMapping("/{id}/status")
    public PedidoResponse mudarStatus(@PathVariable Long id, @Valid @RequestBody MudarStatusRequest req) {
        return pedidoService.mudarStatus(id, req);
    }
}
