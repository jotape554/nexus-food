package com.nexusfood.pedidos.controller;

import com.nexusfood.pedidos.dto.AcompanhamentoPedidoResponse;
import com.nexusfood.pedidos.dto.CriarPedidoRequest;
import com.nexusfood.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Endpoints SEM login usados pelo cliente final: fazer o pedido e acompanhar o andamento. */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PedidoPublicoController {

    private final PedidoService pedidoService;

    @PostMapping("/restaurantes/{slug}/pedidos")
    public AcompanhamentoPedidoResponse criar(@PathVariable String slug, @Valid @RequestBody CriarPedidoRequest req) {
        return pedidoService.criarPublico(slug, req);
    }

    @GetMapping("/pedidos/{codigoPublico}")
    public AcompanhamentoPedidoResponse acompanhar(@PathVariable String codigoPublico) {
        return pedidoService.acompanhar(codigoPublico);
    }
}
