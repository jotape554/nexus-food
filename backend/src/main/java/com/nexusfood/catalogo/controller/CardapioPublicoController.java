package com.nexusfood.catalogo.controller;

import com.nexusfood.catalogo.dto.CardapioResponse;
import com.nexusfood.catalogo.dto.RestaurantePublicoResponse;
import com.nexusfood.catalogo.service.CatalogoService;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.BairroEntregaRepository;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cardápio público por slug (sem login). */
@RestController
@RequestMapping("/public/restaurantes")
@RequiredArgsConstructor
public class CardapioPublicoController {

    private final RestauranteRepository restauranteRepository;
    private final BairroEntregaRepository bairroEntregaRepository;
    private final CatalogoService catalogoService;

    @GetMapping("/{slug}")
    public RestaurantePublicoResponse restaurante(@PathVariable String slug) {
        Restaurante r = buscar(slug);
        return RestaurantePublicoResponse.de(r, bairroEntregaRepository.findAllByRestauranteIdAndAtivoTrueOrderByNomeAsc(r.getId()));
    }

    @GetMapping("/{slug}/cardapio")
    public CardapioResponse cardapio(@PathVariable String slug) {
        return catalogoService.cardapioPublico(buscar(slug).getId());
    }

    private Restaurante buscar(String slug) {
        return restauranteRepository.findBySlug(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }
}
