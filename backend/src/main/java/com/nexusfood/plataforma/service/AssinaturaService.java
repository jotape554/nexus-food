package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.acesso.AcessoPlanoService;
import com.nexusfood.plataforma.config.StripeConfig;
import com.nexusfood.plataforma.dto.AssinaturaResponse;
import com.nexusfood.plataforma.dto.MudancaPlanoResponse;
import com.nexusfood.plataforma.dto.PlanoDisponivelResponse;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assinatura do restaurante e integração com a Stripe. A regra de "o que cada plano libera"
 * fica no {@link AcessoPlanoService}; aqui ficam a situação da assinatura e as mudanças de plano.
 *
 * O pagamento é confirmado só pelo webhook da Stripe (ver StripeWebhookController), nunca pelo
 * navegador. A troca de plano de quem já assina é feita pelo servidor direto na assinatura da
 * Stripe e vale a partir da resposta dela.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaService {

    public static final int DIAS_TRIAL = 14;

    private final RestauranteRepository restauranteRepository;
    private final UsuarioRepository usuarioRepository;
    private final StripeConfig stripeConfig;
    private final StripeGateway stripe;
    private final AcessoPlanoService acessoPlano;
    private final RelogioRestaurante relogio;
    private final Clock clock;

    public List<PlanoDisponivelResponse> listarPlanos() {
        return Arrays.stream(PlanoSaas.values())
                .map(p -> new PlanoDisponivelResponse(p, AcessoPlanoService.nome(p), p.getPrecoMensal(), p.getDescricao(),
                        p.getLimiteUsuarios(), p.getHistoricoDias(),
                        Arrays.stream(Recurso.values()).filter(r -> p.atendeNivelMinimo(r.getPlanoMinimo())).map(Enum::name).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssinaturaResponse status(Long restauranteId, Long usuarioId) {
        Restaurante restaurante = buscar(restauranteId);

        Long diasRestantes = null;
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL && restaurante.getDataFimTrial() != null) {
            diasRestantes = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(clock), restaurante.getDataFimTrial()));
        }

        PlanoSaas efetivo = acessoPlano.planoEfetivo(restaurante);
        Map<String, Boolean> recursos = new LinkedHashMap<>();
        for (Recurso r : Recurso.values()) {
            recursos.put(r.name(), efetivo != null && efetivo.atendeNivelMinimo(r.getPlanoMinimo()));
        }

        PlanoSaas plano = restaurante.getPlanoSaas();
        return new AssinaturaResponse(
                plano,
                plano.getPrecoMensal(),
                plano.getDescricao(),
                restaurante.getStatusAssinaturaSaas(),
                restaurante.getDataFimTrial(),
                diasRestantes,
                efetivo != null,
                efetivo,
                recursos,
                efetivo == null ? null : efetivo.getLimiteUsuarios(),
                usuarioRepository.countByRestauranteIdAndAtivoTrue(restauranteId),
                efetivo == null ? null : efetivo.primeiroDiaDoHistorico(relogio.diaOperacionalAtual(restaurante)),
                !acessoPlano.usuarioDentroDoLimite(restaurante, usuarioId),
                temAssinaturaNaStripe(restaurante));
    }

    /**
     * Escolher um plano. Quem ainda não assina (teste grátis, cancelado) vai para o checkout;
     * quem já assina troca o preço da assinatura existente, com cobrança proporcional, sem
     * criar uma segunda assinatura.
     *
     * Um plano com limite de usuários menor que a equipe ativa é recusado antes de cobrar:
     * o administrador desativa quem sobra e tenta de novo. Nada é apagado.
     */
    @Transactional
    public MudancaPlanoResponse escolherPlano(Long restauranteId, PlanoSaas plano) {
        Restaurante restaurante = buscar(restauranteId);
        String priceId = stripeConfig.priceIdPara(plano);
        if (priceId == null || priceId.isBlank()) {
            throw new RegraDeNegocioException("Pagamentos ainda não configurados para este plano.");
        }

        long ativos = usuarioRepository.countByRestauranteIdAndAtivoTrue(restauranteId);
        Integer limite = plano.getLimiteUsuarios();
        if (limite != null && ativos > limite) {
            long sobra = ativos - limite;
            throw new RegraDeNegocioException("O plano %s permite %d usuários ativos e sua equipe tem %d. Desative %d usuário%s em Equipe antes de mudar."
                    .formatted(AcessoPlanoService.nome(plano), limite, ativos, sobra, sobra == 1 ? "" : "s"));
        }

        try {
            if (temAssinaturaNaStripe(restaurante)) {
                if (restaurante.getPlanoSaas() == plano) {
                    throw new RegraDeNegocioException("Este já é o seu plano.");
                }
                String precoAplicado = stripe.trocarPreco(restaurante.getStripeSubscriptionId(), priceId);
                PlanoSaas aplicado = stripeConfig.planoDoPriceId(precoAplicado);
                restaurante.setPlanoSaas(aplicado != null ? aplicado : plano);
                restauranteRepository.save(restaurante);
                return new MudancaPlanoResponse(null, restaurante.getPlanoSaas(),
                        "Pronto! Seu plano agora é o " + AcessoPlanoService.nome(restaurante.getPlanoSaas())
                                + ". A diferença entra proporcionalmente na próxima fatura.");
            }

            String customerId = garantirCustomerId(restaurante);
            return new MudancaPlanoResponse(stripe.criarCheckout(restaurante, customerId, plano, priceId), plano, null);
        } catch (StripeException e) {
            log.error("Falha ao mudar o plano na Stripe", e);
            throw new RegraDeNegocioException("Não foi possível falar com o sistema de pagamento agora. Tente novamente em instantes.");
        }
    }

    /** URL do portal da Stripe onde o restaurante gerencia forma de pagamento e pode cancelar. */
    public String criarSessaoPortal(Long restauranteId) {
        Restaurante restaurante = buscar(restauranteId);
        if (restaurante.getStripeCustomerId() == null) {
            throw new RegraDeNegocioException("Você ainda não tem uma assinatura para gerenciar.");
        }
        try {
            return stripe.criarPortal(restaurante.getStripeCustomerId());
        } catch (StripeException e) {
            log.error("Falha ao criar sessão do portal de cobrança na Stripe", e);
            throw new RegraDeNegocioException("Não foi possível abrir o portal de cobrança agora.");
        }
    }

    /** Chamado pelo webhook quando checkout.session.completed chega: a assinatura foi paga. */
    @Transactional
    public void confirmarCheckout(String stripeCustomerId, String stripeSubscriptionId, Long restauranteId, PlanoSaas plano) {
        Restaurante restaurante = buscar(restauranteId);
        restaurante.setStripeCustomerId(stripeCustomerId);
        restaurante.setStripeSubscriptionId(stripeSubscriptionId);
        if (plano != null) {
            restaurante.setPlanoSaas(plano);
        }
        restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
        restauranteRepository.save(restaurante);
    }

    /**
     * Chamado pelo webhook customer.subscription.updated: a assinatura mudou na Stripe (troca de
     * plano pelo portal, renovação, falha definitiva de pagamento). "past_due" mantém o acesso
     * enquanto a Stripe tenta cobrar de novo; "canceled"/"unpaid" encerram.
     */
    @Transactional
    public void sincronizarAssinatura(String stripeSubscriptionId, String priceId, String statusStripe) {
        restauranteRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(restaurante -> {
            PlanoSaas plano = stripeConfig.planoDoPriceId(priceId);
            if (plano != null) {
                restaurante.setPlanoSaas(plano);
            }
            if (statusStripe != null) {
                switch (statusStripe) {
                    case "active", "trialing", "past_due" -> restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.ATIVA);
                    case "canceled", "unpaid", "incomplete_expired" -> restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.CANCELADA);
                    default -> { /* incomplete/paused: mantém como está até a Stripe decidir */ }
                }
            }
            restauranteRepository.save(restaurante);
        });
    }

    /** Chamado pelo webhook quando a assinatura é cancelada ou expira na Stripe. */
    @Transactional
    public void marcarCancelada(String stripeSubscriptionId) {
        restauranteRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(restaurante -> {
            restaurante.setStatusAssinaturaSaas(StatusAssinaturaSaas.CANCELADA);
            restauranteRepository.save(restaurante);
        });
    }

    public Event validarEventoWebhook(String payload, String assinatura) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, assinatura, stripeConfig.getWebhookSecret());
    }

    private boolean temAssinaturaNaStripe(Restaurante restaurante) {
        return restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.ATIVA
                && restaurante.getStripeSubscriptionId() != null;
    }

    private String garantirCustomerId(Restaurante restaurante) throws StripeException {
        if (restaurante.getStripeCustomerId() != null) {
            return restaurante.getStripeCustomerId();
        }
        String customerId = stripe.criarCliente(restaurante);
        restaurante.setStripeCustomerId(customerId);
        restauranteRepository.save(restaurante);
        return customerId;
    }

    private Restaurante buscar(Long id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }
}
