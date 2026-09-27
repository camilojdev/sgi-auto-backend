package com.sgi.auto.compartido;

import java.security.SecureRandom;

public final class GeneradorCodigoSeguro {

    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeneradorCodigoSeguro() {
    }

    public static String generar(int longitud) {
        StringBuilder resultado = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            resultado.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return resultado.toString();
    }
}