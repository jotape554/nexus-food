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

    public void enviarRedefinicaoSenha(String destinatario, String link) {
        enviar(destinatario, "Redefinir senha — Nexus Food", """
                Recebemos um pedido para redefinir sua senha no Nexus Food.

                Clique no link abaixo para criar uma nova senha (válido por 1 hora):
                %s

                Se você não pediu isso, pode ignorar este e-mail — sua senha continua a mesma.

                —
                Nexus Food · um produto Nexus Sistemas
                """.formatted(link), link);
    }

    public void enviarConvite(String destinatario, String nome, String restaurante, String link, long validadeHoras) {
        enviar(destinatario, "Convite para a equipe do restaurante " + restaurante + " — Nexus Food", """
                Olá, %s!

                Você agora faz parte da equipe do restaurante %s no Nexus Food.
                Crie sua senha pelo link abaixo (válido por %d horas) e depois entre com este e-mail:
                %s

                —
                Nexus Food · um produto Nexus Sistemas
                """.formatted(nome, restaurante, validadeHoras, link), link);
    }

    /**
     * Sem SENDGRID_API_KEY configurado (ambiente local, ou antes de configurar em produção),
     * só registra o link no log em vez de tentar enviar de verdade.
     */
    private void enviar(String destinatario, String assunto, String texto, String linkParaLog) {
        String chave = apiKey == null ? "" : apiKey.trim();
        String remetenteLimpo = remetente == null ? "" : remetente.trim();

        if (chave.isBlank() || remetenteLimpo.isBlank()) {
            log.info("SENDGRID_API_KEY/MAIL_USERNAME não configurados — envio de e-mail simulado. \"{}\" para {}: {}",
                    assunto, destinatario, linkParaLog);
            return;
        }

        Map<String, Object> corpo = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", destinatario)))),
                "from", Map.of("email", remetenteLimpo),
                "subject", assunto,
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
            log.error("Falha ao enviar e-mail \"{}\" via SendGrid", assunto, e);
        }
    }
}
