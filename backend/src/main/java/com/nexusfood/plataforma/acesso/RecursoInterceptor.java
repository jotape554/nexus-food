package com.nexusfood.plataforma.acesso;

import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica o {@link RequerRecurso} de cada rota. Roda depois dos filtros de segurança: o usuário
 * já está autenticado e o AssinaturaGateFilter já garantiu que a assinatura vale; aqui só se
 * decide se o PLANO inclui o recurso.
 */
@Component
@RequiredArgsConstructor
public class RecursoInterceptor implements HandlerInterceptor {

    private final AcessoPlanoService acessoPlano;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        Recurso recurso = recursoDe(metodo);
        if (recurso == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) return true;

        acessoPlano.exigir(principal.getRestauranteId(), recurso);
        return true;
    }

    static Recurso recursoDe(HandlerMethod metodo) {
        RequerRecurso noMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), RequerRecurso.class);
        if (noMetodo != null) return noMetodo.value();
        if (AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), AcessoLivre.class)) return null;
        RequerRecurso naClasse = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), RequerRecurso.class);
        return naClasse == null ? null : naClasse.value();
    }
}
