package com.nexusfood.plataforma.security;

import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.service.AssinaturaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bloqueia, de forma "elegante" (nunca um 500, nunca a aplicação quebrada), o acesso a um
 * recurso que não está incluído no plano contratado. Roda DEPOIS do AssinaturaGateFilter: aquele cuida de saber
 * se a assinatura está válida; este cuida de saber se o PLANO contratado inclui o recurso.
 *
 * Pra adicionar um novo recurso protegido, basta acrescentar uma entrada no mapa abaixo
 * apontando o prefixo de rota pro Recurso correspondente (ver enum Recurso).
 */
@Component
@RequiredArgsConstructor
public class RecursoGateFilter extends OncePerRequestFilter {

    private static final Map<String, Recurso> RECURSO_POR_PREFIXO = Map.of(
            "/api/categorias", Recurso.CARDAPIO,
            "/api/produtos", Recurso.CARDAPIO,
            "/api/pedidos", Recurso.PEDIDOS,
            "/api/clientes", Recurso.CLIENTES,
            "/api/relatorios", Recurso.RELATORIOS,
            "/api/nexus", Recurso.NEXUS
    );

    private final RestauranteRepository restauranteRepository;
    private final AssinaturaService assinaturaService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        Recurso recurso = recursoDaRota(request.getRequestURI());
        if (recurso == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        Restaurante restaurante = restauranteRepository.findById(principal.getRestauranteId()).orElse(null);
        if (restaurante == null || assinaturaService.recursoLiberado(restaurante, recurso)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(402); // Payment Required
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", 402);
        corpo.put("erro", "Payment Required");
        corpo.put("mensagem", "Este recurso está disponível a partir do plano " + recurso.getPlanoMinimo() + ".");
        corpo.put("upgradeNecessario", true);
        corpo.put("recurso", recurso.name());
        corpo.put("planoNecessario", recurso.getPlanoMinimo().name());
        objectMapper.writeValue(response.getWriter(), corpo);
    }

    private Recurso recursoDaRota(String path) {
        return RECURSO_POR_PREFIXO.entrySet().stream()
                .filter(entrada -> path.startsWith(entrada.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
