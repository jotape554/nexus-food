package com.nexusfood.plataforma.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneUtilTest {

    @Test
    void mesmasVariacoesDoNumeroViramOMesmoTelefone() {
        assertThat(TelefoneUtil.normalizar("(11) 99999-0000")).isEqualTo("5511999990000");
        assertThat(TelefoneUtil.normalizar("11999990000")).isEqualTo("5511999990000");
        assertThat(TelefoneUtil.normalizar("+55 11 99999-0000")).isEqualTo("5511999990000");
        assertThat(TelefoneUtil.normalizar("011 99999 0000")).isEqualTo("5511999990000");
    }

    @Test
    void aceitaFixoCom10Digitos() {
        assertThat(TelefoneUtil.normalizar("(21) 3333-4444")).isEqualTo("552133334444");
    }

    @Test
    void recusaNumerosInvalidos() {
        assertThat(TelefoneUtil.normalizar(null)).isNull();
        assertThat(TelefoneUtil.normalizar("")).isNull();
        assertThat(TelefoneUtil.normalizar("99999-0000")).isNull();
        assertThat(TelefoneUtil.normalizar("+1 415 555 0000")).isNull();
        assertThat(TelefoneUtil.normalizar("123456789012345")).isNull();
    }
}
