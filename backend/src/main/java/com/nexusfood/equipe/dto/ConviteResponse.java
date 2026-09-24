package com.nexusfood.equipe.dto;

import java.time.Instant;

/**
 * Link para a pessoa criar a própria senha. Também vai por e-mail; aparece aqui para o
 * administrador poder mandar pelo WhatsApp. Ninguém além da própria pessoa conhece a senha.
 */
public record ConviteResponse(UsuarioEquipeResponse usuario, String link, Instant expiraEm) {}
