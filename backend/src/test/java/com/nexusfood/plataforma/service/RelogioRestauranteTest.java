package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.model.Restaurante;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RelogioRestauranteTest {

    private final RelogioRestaurante relogio =
            new RelogioRestaurante(Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC));

    private Restaurante restaurante(LocalTime virada) {
        return Restaurante.builder().nome("R").slug("r").horaViradaDia(virada).build();
    }

    @Test
    void pedidoDeMadrugadaAntesDaViradaContaNoDiaAnterior() {
        // 03:40 UTC = 00:40 de sábado em São Paulo → ainda é sexta no dia operacional
        Instant sabado0040 = Instant.parse("2026-09-26T03:40:00Z");
        assertThat(relogio.diaOperacional(restaurante(LocalTime.of(4, 0)), sabado0040))
                .isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test
    void depoisDaViradaJaEONovoDia() {
        Instant sabado0400 = Instant.parse("2026-09-26T07:00:00Z"); // 04:00 em São Paulo
        assertThat(relogio.diaOperacional(restaurante(LocalTime.of(4, 0)), sabado0400))
                .isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void usaOFusoDoRestauranteENaoODoServidor() {
        // 01:00 UTC de quinta = 22:00 de quarta em São Paulo
        Instant instante = Instant.parse("2026-09-24T01:00:00Z");
        assertThat(relogio.diaOperacional(restaurante(LocalTime.MIDNIGHT), instante))
                .isEqualTo(LocalDate.of(2026, 9, 23));
    }
}
