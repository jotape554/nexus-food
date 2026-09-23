package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.model.Restaurante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Único lugar que responde "que horas são" e "que dia é" para um restaurante. O dia
 * operacional respeita o fuso e a hora de virada do restaurante: com virada às 04:00, tudo
 * que acontece entre 00:00 e 03:59 ainda pertence ao dia anterior.
 */
@Component
@RequiredArgsConstructor
public class RelogioRestaurante {

    private final Clock clock;

    public Instant agora() {
        return Instant.now(clock);
    }

    public LocalDate diaOperacional(Restaurante restaurante, Instant instante) {
        ZonedDateTime local = instante.atZone(ZoneId.of(restaurante.getFusoHorario()));
        LocalDate data = local.toLocalDate();
        return local.toLocalTime().isBefore(restaurante.getHoraViradaDia()) ? data.minusDays(1) : data;
    }

    public LocalDate diaOperacionalAtual(Restaurante restaurante) {
        return diaOperacional(restaurante, agora());
    }
}
