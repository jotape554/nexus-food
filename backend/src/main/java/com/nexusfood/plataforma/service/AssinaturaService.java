package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.config.StripeConfig;
import com.nexusfood.plataforma.dto.AssinaturaResponse;
import com.nexusfood.plataforma.dto.PlanoDisponivelResponse;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.enums.StatusAssinaturaSaas;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Centraliza a regra de acesso ao painel e a integração com a Stripe. O acesso é liberado
 * quando status == ATIVA, ou quando ainda está dentro do período de teste gratuito.
 * A confirmação de pagamento chega de forma assíncrona pelo webhook da Stripe
 * (ver {@link com.nexusfood.plataforma.controller.StripeWebhookController}), nunca diretamente
 * do navegador do usuário — evita que alguém "libere o próprio acesso" manipulando o front.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssinaturaService {

    public static final int DIAS_TRIAL = 14;

    private final RestauranteRepository restauranteRepository;
    private final StripeConfig stripeConfig;
    private final Clock clock;

    public List<PlanoDisponivelResponse> listarPlanos() {
        return Arrays.stream(PlanoSaas.values())
                .map(p -> new PlanoDisponivelResponse(p, p.getPrecoMensal(), p.getDescricao()))
                .toList();
    }

    public AssinaturaResponse status(Long restauranteId) {
        return paraResponse(buscar(restauranteId));
    }

    /** Cria (ou reaproveita) o cliente na Stripe e devolve a URL de checkout para o plano escolhido. */
    @Transactional
    public String criarSessaoCheckout(Long restauranteId, PlanoSaas plano) {
        Restaurante restaurante = buscar(restauranteId);
        String priceId = stripeConfig.priceIdPara(plano);
        if (priceId == null || priceId.isBlank()) {
            throw new RegraDeNegocioException("Pagamentos ainda não configurados para este plano.");
        }

        try {
            String customerId = garantirCustomerId(restaurante);

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setClientReferenceId(String.valueOf(restaurante.getId()))
                    .putMetadata("restauranteId", String.valueOf(restaurante.getId()))
                    .putMetadata("plano", plano.name())
                    .setSuccessUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura?checkout=sucesso")
                    .setCancelUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura?checkout=cancelado")
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .build();

            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Falha ao criar sessão de checkout na Stripe", e);
            throw new RegraDeNegocioException("Não foi possível iniciar o pagamento agora. Tente novamente em instantes.");
        }
    }

    /** URL do portal da Stripe onde o restaurante gerencia forma de pagamento e pode cancelar. */
    public String criarSessaoPortal(Long restauranteId) {
        Restaurante restaurante = buscar(restauranteId);
        if (restaurante.getStripeCustomerId() == null) {
            throw new RegraDeNegocioException("Você ainda não tem uma assinatura para gerenciar.");
        }
        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(restaurante.getStripeCustomerId())
                            .setReturnUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura")
                            .build();
            return com.stripe.model.billingportal.Session.create(params).getUrl();
        } catch (StripeException e) {
            log.error("Falha ao criar sessão do portal de cobrança na Stripe", e);
            throw new RegraDeNegocioException("Não foi possível abrir o portal de cobrança agora.");
        }
    }

    /** Chamado pelo webhook quando checkout.session.completed chega — a assinatura foi paga. */
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

    public boolean acessoLiberado(Restaurante restaurante) {
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.ATIVA) {
            return true;
        }
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL) {
            LocalDate fimTrial = restaurante.getDataFimTrial();
            return fimTrial != null && !LocalDate.now(clock).isAfter(fimTrial);
        }
        return false;
    }

    /**
     * Durante o trial, libera todos os recursos pra restaurante poder explorar o sistema por
     * inteiro antes de decidir o plano. Depois que assina de verdade (ATIVA), passa a valer
     * o recorte real do plano contratado. Sem assinatura válida, o AssinaturaGateFilter já
     * bloqueia o painel inteiro antes disso ser sequer avaliado.
     */
    public boolean recursoLiberado(Restaurante restaurante, Recurso recurso) {
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL) {
            return acessoLiberado(restaurante);
        }
        if (restaurante.getStatusAssinaturaSaas() != StatusAssinaturaSaas.ATIVA) {
            return false;
        }
        return restaurante.getPlanoSaas().atendeNivelMinimo(recurso.getPlanoMinimo());
    }

    private String garantirCustomerId(Restaurante restaurante) throws StripeException {
        if (restaurante.getStripeCustomerId() != null) {
            return restaurante.getStripeCustomerId();
        }
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(restaurante.getNome())
                .putMetadata("restauranteId", String.valueOf(restaurante.getId()))
                .build();
        Customer customer = Customer.create(params);
        restaurante.setStripeCustomerId(customer.getId());
        restauranteRepository.save(restaurante);
        return customer.getId();
    }

    private AssinaturaResponse paraResponse(Restaurante restaurante) {
        Long diasRestantes = null;
        if (restaurante.getStatusAssinaturaSaas() == StatusAssinaturaSaas.TRIAL && restaurante.getDataFimTrial() != null) {
            diasRestantes = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(clock), restaurante.getDataFimTrial()));
        }

        PlanoSaas plano = restaurante.getPlanoSaas();
        return new AssinaturaResponse(
                plano,
                plano.getPrecoMensal(),
                plano.getDescricao(),
                restaurante.getStatusAssinaturaSaas(),
                restaurante.getDataFimTrial(),
                diasRestantes,
                acessoLiberado(restaurante)
        );
    }

    private Restaurante buscar(Long id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrada"));
    }
}
