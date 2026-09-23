package com.nexusfood.plataforma.util;

/** Gera CPFs válidos (dígitos verificadores corretos) e únicos por seed, só para os testes. */
public final class CpfTestFixture {

    private CpfTestFixture() {
    }

    public static String gerar(String seed) {
        int base = Math.abs(seed.hashCode()) % 1_000_000_000;
        int[] numeros = new int[11];
        String baseStr = String.format("%09d", base);
        for (int i = 0; i < 9; i++) {
            numeros[i] = baseStr.charAt(i) - '0';
        }
        numeros[9] = digitoVerificador(numeros, 9);
        numeros[10] = digitoVerificador(numeros, 10);

        StringBuilder sb = new StringBuilder();
        for (int n : numeros) sb.append(n);
        return sb.toString();
    }

    private static int digitoVerificador(int[] numeros, int quantidade) {
        int soma = 0;
        int peso = quantidade + 1;
        for (int i = 0; i < quantidade; i++) {
            soma += numeros[i] * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
