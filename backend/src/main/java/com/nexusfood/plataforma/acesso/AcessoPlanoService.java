package com.nexusfood.plataforma.acesso;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Único lugar que responde "o que este restaurante pode usar agora".
 *
 * Plano efetivo: durante o teste grátis vale o PREMIUM (o restaurante conhece tudo antes de
 * escolher); com a assinatura ativa, o plano contratado; sem assinatura válida, nenhum (o
 * AssinaturaGateFilter já barra o painel antes). Downgrade nunca apaga dados: só muda o que se lê.
 */
@Service
@RequiredArgsConstructor
public class AcessoPlanoService {

    private final RestauranteRepository restauranteRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public boolean acessoLiberado(Restaurante restaurante) {
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.ATIVA) return true;
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL) {
            LocalDate fimTrial = restaurante.getDataFimTrial();
            return fimTrial != null && !LocalDate.now(clock).isAfter(fimTrial);
        }
        return false;
    }

    /** null quando a assinatura não está valendo. */
    public PlanoSaas planoEfetivo(Restaurante restaurante) {
        if (!acessoLiberado(restaurante)) return null;
        return restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL
                ? PlanoSaas.PREMIUM
                : restaurante.getPlanoSaas();
    }

    public PlanoSaas planoEfetivo(Long restauranteId) {
        return planoEfetivo(buscar(restauranteId));
    }

    public boolean liberado(Restaurante restaurante, Recurso recurso) {
        PlanoSaas plano = planoEfetivo(restaurante);
        return plano != null && plano.atendeNivelMinimo(recurso.getPlanoMinimo());
    }

    public boolean liberado(Long restauranteId, Recurso recurso) {
        return liberado(buscar(restauranteId), recurso);
    }

    public void exigir(Long restauranteId, Recurso recurso) {
        if (!liberado(restauranteId, recurso)) {
            throw new PlanoInsuficienteException(recurso.name(), recurso.getPlanoMinimo(),
                    recurso.getNome() + " faz parte do plano " + nome(recurso.getPlanoMinimo()) + " ou superior.");
        }
    }

    /** Limite de usuários ativos do plano efetivo (null = sem limite ou sem plano valendo). */
    public Integer limiteUsuarios(Restaurante restaurante) {
        PlanoSaas plano = planoEfetivo(restaurante);
        return plano == null ? null : plano.getLimiteUsuarios();
    }

    /**
     * Se o plano diminuiu por fora (pelo portal da Stripe, por exemplo) e a equipe ficou maior que
     * o limite, os últimos a entrar ficam sem acesso até o administrador ajustar a equipe. Os
     * administradores vêm primeiro na fila, então o dono nunca fica trancado para fora.
     */
    public boolean usuarioDentroDoLimite(Restaurante restaurante, Long usuarioId) {
        Integer limite = limiteUsuarios(restaurante);
        if (limite == null) return true;
        List<Long> fila = usuarioRepository.idsAtivosPorPrioridade(restaurante.getId());
        int posicao = fila.indexOf(usuarioId);
        return posicao >= 0 && posicao < limite;
    }

    public static String nome(PlanoSaas plano) {
        return switch (plano) {
            case BASICO -> "Básico";
            case PROFISSIONAL -> "Profissional";
            case PREMIUM -> "Premium";
        };
    }

    private Restaurante buscar(Long id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }
}
