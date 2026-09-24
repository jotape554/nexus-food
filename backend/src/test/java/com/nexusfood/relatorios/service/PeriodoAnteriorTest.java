package com.nexusfood.relatorios.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodoAnteriorTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void mesAteHojeComparaComOMesmoTrechoDoMesAnterior() {
        assertThat(RelatorioVendasService.periodoAnterior(d("2026-09-01"), d("2026-09-24")))
                .containsExactly(d("2026-08-01"), d("2026-08-24"));
    }

    @Test
    void mesInteiroComparaComOMesAnteriorInteiro() {
        assertThat(RelatorioVendasService.periodoAnterior(d("2026-09-01"), d("2026-09-30")))
                .containsExactly(d("2026-08-01"), d("2026-08-31"));
        assertThat(RelatorioVendasService.periodoAnterior(d("2026-03-01"), d("2026-03-31")))
                .containsExactly(d("2026-02-01"), d("2026-02-28"));
    }

    @Test
    void dozeMesesComparaComOAnoAnterior() {
        assertThat(RelatorioVendasService.periodoAnterior(d("2025-10-01"), d("2026-09-30")))
                .containsExactly(d("2024-10-01"), d("2025-09-30"));
    }

    @Test
    void ultimosDiasComparaComOMesmoNumeroDeDiasAntes() {
        assertThat(RelatorioVendasService.periodoAnterior(d("2026-09-18"), d("2026-09-24")))
                .containsExactly(d("2026-09-11"), d("2026-09-17"));
    }
}
