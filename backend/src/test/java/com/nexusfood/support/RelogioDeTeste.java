package com.nexusfood.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock ajustável para os testes: dá para parar o tempo, avançar minutos ou pular para um instante. */
public class RelogioDeTeste extends Clock {

    private volatile Instant agora;

    public RelogioDeTeste(Instant inicio) {
        this.agora = inicio;
    }

    public void definir(Instant instante) {
        this.agora = instante;
    }

    public void avancar(Duration duracao) {
        this.agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
