package com.nexusfood.plataforma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.acesso.AcessoPlanoService;
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
 * Bloqueia o painel administrativo (não o cardápio público) quando o período de
 * teste do restaurante expira e nenhum plano foi ativado, e também o usuário que ficou além do
 * limite de usuários do plano. Roda depois do JwtAuthFilter, então já existe (ou não) uma
 * autenticação no SecurityContext quando este filtro é executado.
 *
 * /api/assinatura fica sempre aberto: é por ali que a situação se resolve.
 */
@Component
@RequiredArgsConstructor
public class AssinaturaGateFilter extends OncePerRequestFilter {

    private final RestauranteRepository restauranteRepository;
    private final AcessoPlanoService acessoPlano;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        boolean isolado = !path.startsWith("/api/") || path.startsWith("/api/assinatura");
        if (isolado) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        Restaurante restaurante = restauranteRepository.findById(principal.getRestauranteId()).orElse(null);
        if (restaurante == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!acessoPlano.acessoLiberado(restaurante)) {
            responder(response, "Seu período de teste terminou. Escolha um plano para continuar usando o Nexus Food.", Map.of());
            return;
        }
        if (!acessoPlano.usuarioDentroDoLimite(restaurante, principal.getId())) {
            responder(response, "O plano atual do restaurante tem menos usuários do que a equipe cadastrada. "
                    + "Peça ao administrador para ajustar a equipe ou o plano.", Map.of("usuarioForaDoLimite", true));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void responder(HttpServletResponse response, String mensagem, Map<String, Object> extras) throws IOException {
        response.setStatus(402); // Payment Required
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", 402);
        corpo.put("erro", "Payment Required");
        corpo.put("mensagem", mensagem);
        corpo.putAll(extras);
        objectMapper.writeValue(response.getWriter(), corpo);
    }
}
