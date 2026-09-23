package com.nexusfood.plataforma.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Manda e-mail via API HTTP do SendGrid, não SMTP — plataformas de hospedagem como o
 * Railway costumam bloquear as portas de SMTP (587/465) para evitar spam, mas nunca
 * bloqueiam HTTPS.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final URI SENDGRID_URL = URI.create("https://api.sendgrid.com/v3/mail/send");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${sendgrid.api-key:}")
    private String apiKey;

    @Value("${spring.mail.username:}")
    private String remetente;

    /**
     * Sem SENDGRID_API_KEY configurado (ambiente local, ou antes de configurar em produção),
     * só registra o link no log em vez de tentar enviar de verdade.
     */
    public void enviarRedefinicaoSenha(String destinatario, String link) {
        String chave = apiKey == null ? "" : apiKey.trim();
        String remetenteLimpo = remetente == null ? "" : remetente.trim();

        if (chave.isBlank() || remetenteLimpo.isBlank()) {
            log.info("SENDGRID_API_KEY/MAIL_USERNAME não configurados — envio de e-mail simulado. Link de redefinição para {}: {}",
                    destinatario, link);
            return;
        }

        String texto = """
                Recebemos um pedido para redefinir sua senha no Nexus Food.

                Clique no link abaixo para criar uma nova senha (válido por 1 hora):
                %s

                Se você não pediu isso, pode ignorar este e-mail — sua senha continua a mesma.
                """.formatted(link);

        Map<String, Object> corpo = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", destinatario)))),
                "from", Map.of("email", remetenteLimpo),
                "subject", "Redefinir senha — Nexus Food",
                "content", List.of(Map.of("type", "text/plain", "value", texto))
        );

        try {
            String json = objectMapper.writeValueAsString(corpo);
            HttpRequest request = HttpRequest.newBuilder(SENDGRID_URL)
                    .header("Authorization", "Bearer " + chave)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("SendGrid recusou o envio (status {}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de redefinição de senha via SendGrid", e);
        }
    }
}
