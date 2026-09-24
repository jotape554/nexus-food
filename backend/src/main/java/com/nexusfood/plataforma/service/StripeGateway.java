package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.config.StripeConfig;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.model.Restaurante;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Todas as chamadas de rede para a Stripe. Separado do AssinaturaService para as regras de
 * assinatura serem testadas sem rede (os testes trocam este bean por um dublê).
 */
@Component
@RequiredArgsConstructor
public class StripeGateway {

    private final StripeConfig stripeConfig;

    public String criarCliente(Restaurante restaurante) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(restaurante.getNome())
                .putMetadata("restauranteId", String.valueOf(restaurante.getId()))
                .build();
        return Customer.create(params).getId();
    }

    public String criarCheckout(Restaurante restaurante, String customerId, PlanoSaas plano, String priceId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setClientReferenceId(String.valueOf(restaurante.getId()))
                .putMetadata("restauranteId", String.valueOf(restaurante.getId()))
                .putMetadata("plano", plano.name())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("restauranteId", String.valueOf(restaurante.getId()))
                        .build())
                .setSuccessUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura?checkout=sucesso")
                .setCancelUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura?checkout=cancelado")
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                .build();
        return Session.create(params).getUrl();
    }

    public String criarPortal(String customerId) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(stripeConfig.getFrontendUrl() + "/painel/assinatura")
                        .build();
        return com.stripe.model.billingportal.Session.create(params).getUrl();
    }

    /**
     * Troca o preço da assinatura existente (upgrade ou downgrade), com cobrança proporcional
     * na próxima fatura. Nunca cria uma segunda assinatura. Devolve o price id que ficou valendo.
     */
    public String trocarPreco(String subscriptionId, String priceId) throws StripeException {
        Subscription assinatura = Subscription.retrieve(subscriptionId);
        SubscriptionItem item = assinatura.getItems().getData().get(0);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder().setId(item.getId()).setPrice(priceId).build())
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                .build();
        Subscription atualizada = assinatura.update(params);
        return atualizada.getItems().getData().get(0).getPrice().getId();
    }
}
