package com.nexusfood.plataforma.controller;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.service.AssinaturaService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único ponto por onde a Stripe confirma pagamentos — nunca confie em nada que o navegador do
 * usuário mande diretamente para liberar acesso. A assinatura do webhook garante que a chamada
 * veio mesmo da Stripe (ver AssinaturaService.validarEventoWebhook).
 */
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final AssinaturaService assinaturaService;

    @PostMapping
    public ResponseEntity<String> receber(@RequestBody String payload,
                                           @RequestHeader("Stripe-Signature") String assinatura) {
        Event event;
        try {
            event = assinaturaService.validarEventoWebhook(payload, assinatura);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook da Stripe com assinatura inválida");
            return ResponseEntity.badRequest().body("assinatura inválida");
        }

        // deserializeUnsafe() em vez de getObject(): getObject() compara a versão da API do
        // evento com a versão compilada no SDK e lança NullPointerException quando o campo
        // api_version não bate (ou está ausente) — não vale a pena depender disso só para ler
        // customer/subscription/metadata, que não mudam entre versões da API da Stripe.
        StripeObject objeto;
        try {
            objeto = event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.error("Falha ao deserializar payload do evento {} da Stripe", event.getType(), e);
            return ResponseEntity.ok("ok");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                if (objeto instanceof Session session) {
                    Long restauranteId = extrairRestauranteId(session.getMetadata());
                    PlanoSaas plano = extrairPlano(session.getMetadata());
                    if (restauranteId != null) {
                        assinaturaService.confirmarCheckout(
                                session.getCustomer(), session.getSubscription(), restauranteId, plano);
                    }
                }
            }
            case "customer.subscription.deleted" -> {
                if (objeto instanceof Subscription subscription) {
                    assinaturaService.marcarCancelada(subscription.getId());
                }
            }
            default -> log.debug("Evento da Stripe ignorado: {}", event.getType());
        }

        return ResponseEntity.ok("ok");
    }

    private Long extrairRestauranteId(java.util.Map<String, String> metadata) {
        if (metadata == null || metadata.get("restauranteId") == null) return null;
        try {
            return Long.parseLong(metadata.get("restauranteId"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PlanoSaas extrairPlano(java.util.Map<String, String> metadata) {
        if (metadata == null || metadata.get("plano") == null) return null;
        try {
            return PlanoSaas.valueOf(metadata.get("plano"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
