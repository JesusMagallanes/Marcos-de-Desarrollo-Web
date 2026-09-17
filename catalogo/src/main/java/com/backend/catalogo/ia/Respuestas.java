package com.backend.catalogo.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.backend.catalogo.ia.dto.IaDtos.Riesgo;

import tools.jackson.databind.JsonNode;

/**
 * Leer lo que devuelve un modelo sin fiarse de su forma.
 *
 * <p>Un modelo puede devolver un número donde se pidió texto, una lista de
 * cuarenta elementos donde se pidieron tres o un párrafo de dos mil caracteres.
 * Todo lo que sale de aquí está acotado en tipo y en tamaño, que es lo que
 * permite pintarlo sin sorpresas.
 */
final class Respuestas {

    private Respuestas() {
    }

    static String texto(JsonNode nodo, String campo, int maximo) {
        String valor = nodo.path(campo).isString() ? nodo.path(campo).asString().trim() : "";
        return valor.length() > maximo ? valor.substring(0, maximo).trim() + "…" : valor;
    }

    static List<String> lista(JsonNode nodo, String campo, int elementos, int maximo) {
        List<String> salida = new ArrayList<>();
        for (JsonNode e : nodo.path(campo)) {
            if (salida.size() >= elementos) {
                break;
            }
            if (e.isString() && !e.asString().isBlank()) {
                String v = e.asString().trim();
                salida.add(v.length() > maximo ? v.substring(0, maximo).trim() + "…" : v);
            }
        }
        return salida;
    }

    /** Lo que no sea BAJO, MEDIO o ALTO se lee como MEDIO: ante la duda, que alguien mire. */
    static Riesgo riesgo(JsonNode nodo) {
        try {
            return Riesgo.valueOf(nodo.path("riesgo").asString("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconocido) {
            return Riesgo.MEDIO;
        }
    }

    static Riesgo mayor(Riesgo a, Riesgo b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
