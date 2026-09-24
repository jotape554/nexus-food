package com.nexusfood.plataforma.config;

import com.nexusfood.plataforma.enums.PlanoSaas;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@Getter
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.price.basico}")
    private String precoBasico;

    @Value("${stripe.price.profissional}")
    private String precoProfissional;

    @Value("${stripe.price.premium}")
    private String precoPremium;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private Map<PlanoSaas, String> precoPorPlano;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        precoPorPlano = Map.of(
                PlanoSaas.BASICO, precoBasico,
                PlanoSaas.PROFISSIONAL, precoProfissional,
                PlanoSaas.PREMIUM, precoPremium
        );
    }

    public String priceIdPara(PlanoSaas plano) {
        return precoPorPlano.get(plano);
    }

    /** Caminho inverso, para os eventos da Stripe que só trazem o price id. */
    public PlanoSaas planoDoPriceId(String priceId) {
        if (priceId == null) return null;
        return precoPorPlano.entrySet().stream()
                .filter(e -> priceId.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
