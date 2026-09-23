package com.nexusfood.clientes.controller;

import com.nexusfood.clientes.model.Cliente;
import com.nexusfood.clientes.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ClienteService clienteService;

    @GetMapping
    public Page<Cliente> listar(@RequestParam(required = false) String busca,
                                @RequestParam(defaultValue = "0") int pagina,
                                @RequestParam(defaultValue = "20") int tamanho) {
        int tamanhoSeguro = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO_PAGINA));
        return clienteService.listar(busca, PageRequest.of(Math.max(0, pagina), tamanhoSeguro));
    }
}
