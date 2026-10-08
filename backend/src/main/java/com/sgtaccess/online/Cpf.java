package com.sgtaccess.online;

/** CPF: normalização e dígito verificador (espelha o `com.sgtaccess.visitas.Cpf` do SGT). */
public final class Cpf {

    private Cpf() {
    }

    public static String digitos(String texto) {
        if (texto == null || !texto.matches("[0-9.\\- ]+")) {
            return null;
        }
        return texto.replaceAll("[^0-9]", "");
    }

    public static boolean valido(String digitos) {
        if (digitos == null || !digitos.matches("[0-9]{11}") || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return dv(digitos, 9) == digitos.charAt(9) - '0' && dv(digitos, 10) == digitos.charAt(10) - '0';
    }

    private static int dv(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
