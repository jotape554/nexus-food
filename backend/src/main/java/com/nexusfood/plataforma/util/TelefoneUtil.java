package com.nexusfood.plataforma.util;

/**
 * O telefone é a identidade do cliente no restaurante, então "(11) 99999-0000",
 * "11999990000" e "+55 11 99999-0000" precisam virar a mesma coisa: só dígitos, com DDI 55.
 */
public final class TelefoneUtil {

    private TelefoneUtil() {}

    /** Devolve o telefone normalizado (ex.: 5511999990000) ou null se não for um telefone brasileiro válido. */
    public static String normalizar(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("0")) {
            digitos = digitos.replaceFirst("^0+", "");
        }
        if (digitos.length() == 10 || digitos.length() == 11) {
            digitos = "55" + digitos;
        }
        if (!digitos.startsWith("55") || (digitos.length() != 12 && digitos.length() != 13)) {
            return null;
        }
        if (digitos.charAt(2) == '0') {
            return null; // DDD nunca começa com 0
        }
        if (digitos.length() == 13 && digitos.charAt(4) != '9') {
            return null; // celular com 9 dígitos sempre começa com 9
        }
        return digitos;
    }
}
