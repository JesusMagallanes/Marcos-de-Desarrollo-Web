package com.backend.catalogo.producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;

/**
 * Todo lo que estrecha una búsqueda: precio, marcas, atributos, disponibilidad
 * y orden. Un value object para no arrastrar ocho parámetros sueltos por el
 * servicio y el repositorio.
 *
 * <h4>El centinela</h4>
 *
 * <p>Ni las marcas ni los atributos pueden viajar como lista vacía a un
 * {@code IN}: es un error de sintaxis. Cuando no hay filtro, la lista lleva un
 * valor imposible y un booleano aparte le dice a la consulta que no lo mire. Es
 * el idioma de {@code NINGUNO} que ya usa descubrimiento.
 *
 * <h4>Los atributos</h4>
 *
 * <p>Llegan como {@code codigo:valor}, la misma forma que la página de
 * categoría seleccionaba en el navegador. Varios códigos distintos se exigen
 * TODOS; dos valores del mismo código son alternativa. {@code numCodigos} es
 * cuántos códigos distintos hay que cumplir, que es lo que la subconsulta
 * compara.
 */
public record FiltroProductos(
        BigDecimal precioMin,
        BigDecimal precioMax,
        List<Long> marcaIds,
        List<String> atributos,
        boolean soloDisponibles,
        Orden orden) {

    /** Marca imposible para que {@code IN} nunca reciba una lista vacía. */
    private static final List<Long> MARCA_NINGUNA = List.of(-1L);
    /**
     * Token imposible, por lo mismo. Se usa {@code U+FFFF} (un noncharacter
     * permanente de Unicode) y no {@code U+0000}: PostgreSQL rechaza el byte
     * nulo en cualquier texto, aun cuando el guardian {@code :numAtributos = 0}
     * corta la comparacion, porque el parametro se vincula igual.
     */
    private static final List<String> ATRIBUTO_NINGUNO = List.of("\uFFFF:\uFFFF");

    public enum Orden {
        RELEVANCIA, PRECIO_ASC, PRECIO_DESC, NOMBRE_ASC, NOMBRE_DESC, NOVEDAD;

        public static Orden de(String valor) {
            if (valor == null || valor.isBlank()) {
                return RELEVANCIA;
            }
            try {
                return valueOf(valor.trim().toUpperCase());
            } catch (IllegalArgumentException desconocido) {
                return RELEVANCIA;
            }
        }
    }

    public boolean sinMarcas() {
        return marcaIds == null || marcaIds.isEmpty();
    }

    /** La lista que va a la consulta: la real, o el centinela si no hay. */
    public List<Long> marcasParaConsulta() {
        return sinMarcas() ? MARCA_NINGUNA : marcaIds;
    }

    /** Cuántos códigos de atributo distintos hay que cumplir. */
    public int numCodigos() {
        return (int) atributosLimpios().stream()
                .map(a -> a.split(":", 2)[0])
                .distinct()
                .count();
    }

    public List<String> atributosParaConsulta() {
        List<String> limpios = atributosLimpios();
        return limpios.isEmpty() ? ATRIBUTO_NINGUNO : limpios;
    }

    private List<String> atributosLimpios() {
        if (atributos == null) {
            return List.of();
        }
        List<String> limpios = new ArrayList<>();
        for (String a : atributos) {
            if (a != null && a.contains(":") && !a.isBlank()) {
                limpios.add(a.trim());
            }
        }
        return limpios;
    }

    /**
     * El orden como {@code Sort}, SIEMPRE con id de desempate.
     *
     * <p>Sin el desempate, dos productos del mismo precio podrían intercambiarse
     * entre dos consultas y una página repetir o saltar un producto. El id lo
     * hace determinista, que es lo que pide el bloque.
     */
    public Sort comoSort() {
        Sort id = Sort.by("id").ascending();
        return switch (orden == null ? Orden.RELEVANCIA : orden) {
            case PRECIO_ASC -> Sort.by("precio").ascending().and(id);
            case PRECIO_DESC -> Sort.by("precio").descending().and(id);
            case NOMBRE_ASC -> Sort.by("name").ascending().and(id);
            case NOMBRE_DESC -> Sort.by("name").descending().and(id);
            case NOVEDAD -> Sort.by("creadoEn").descending().and(id);
            case RELEVANCIA -> id;
        };
    }
}
