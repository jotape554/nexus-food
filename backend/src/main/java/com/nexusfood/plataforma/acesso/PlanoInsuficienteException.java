package com.nexusfood.plataforma.acesso;

import com.nexusfood.plataforma.enums.PlanoSaas;
import lombok.Getter;

import java.util.Map;

/**
 * O plano atual não inclui o que foi pedido. Vira 402 com {@code upgradeNecessario: true} e o
 * plano que resolve, para a tela oferecer o upgrade em vez de mostrar um erro.
 */
@Getter
public class PlanoInsuficienteException extends RuntimeException {

    private final String recurso;
    private final PlanoSaas planoNecessario;
    private final Map<String, Object> detalhes;

    public PlanoInsuficienteException(String recurso, PlanoSaas planoNecessario, String mensagem) {
        this(recurso, planoNecessario, mensagem, Map.of());
    }

    public PlanoInsuficienteException(String recurso, PlanoSaas planoNecessario, String mensagem, Map<String, Object> detalhes) {
        super(mensagem);
        this.recurso = recurso;
        this.planoNecessario = planoNecessario;
        this.detalhes = detalhes;
    }
}
