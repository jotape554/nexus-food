package com.nexusfood.relatorios.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Como a série do relatório é fatiada. Semana = segunda a domingo (padrão ISO, o do calendário brasileiro). */
public enum Agrupamento {
    DIA,
    SEMANA,
    MES;

    public LocalDate inicioDoGrupo(LocalDate dia) {
        return switch (this) {
            case DIA -> dia;
            case SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> dia.withDayOfMonth(1);
        };
    }

    public LocalDate fimDoGrupo(LocalDate inicioDoGrupo) {
        return switch (this) {
            case DIA -> inicioDoGrupo;
            case SEMANA -> inicioDoGrupo.plusDays(6);
            case MES -> inicioDoGrupo.withDayOfMonth(inicioDoGrupo.lengthOfMonth());
        };
    }
}
