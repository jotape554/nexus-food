package com.nexusfood.plataforma.util;

/** Validação de CPF pelo algoritmo oficial dos dígitos verificadores. */
public final class CpfValidator {

    private CpfValidator() {
    }

    public static String somenteDigitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    public static boolean isValido(String cpf) {
        String digitos = somenteDigitos(cpf);
        if (digitos.length() != 11 || digitos.chars().allMatch(c -> c == digitos.charAt(0))) {
            return false;
        }

        int[] numeros = digitos.chars().map(c -> c - '0').toArray();
        return numeros[9] == digitoVerificador(numeros, 9) && numeros[10] == digitoVerificador(numeros, 10);
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
