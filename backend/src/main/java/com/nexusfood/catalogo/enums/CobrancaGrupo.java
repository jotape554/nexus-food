package com.nexusfood.catalogo.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Como as opções escolhidas num grupo entram no preço do item. */
public enum CobrancaGrupo {
    /** Cada opção escolhida soma o seu preço (adicionais, borda, tamanho). */
    SOMA,
    /** Só a opção mais cara conta (pizza meio a meio cobrada pelo sabor mais caro). */
    MAIOR,
    /** Média das opções escolhidas (meio a meio cobrado pela média dos sabores). */
    MEDIA;

    public BigDecimal valor(List<BigDecimal> precos) {
        if (precos.isEmpty()) return BigDecimal.ZERO;
        return switch (this) {
            case SOMA -> precos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case MAIOR -> precos.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case MEDIA -> precos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(precos.size()), 2, RoundingMode.HALF_UP);
        };
    }
}
