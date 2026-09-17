package com.backend.catalogo.ia;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.descubrimiento.RecomendacionService;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;
import com.backend.catalogo.ia.dto.IaDtos.Complementos;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import tools.jackson.databind.JsonNode;

/**
 * «A esta laptop le falta un mouse»: accesorios que completan el carrito.
 *
 * <h4>Candidatos del recomendador, criterio de la IA</h4>
 *
 * <p>Los candidatos salen de {@code RecomendacionService.similares} —co-visita y
 * parecido por ficha, lo que ya se calcula— de cada producto del carrito, sin
 * los que ya están dentro y solo con stock. El problema es que «parecido» mete
 * otra laptop junto a la laptop, que es justo lo que NO completa un carrito.
 * Eso es lo que decide el modelo: cuáles de esos candidatos son complementos de
 * verdad, con una razón de una línea. No puede proponer nada fuera de la lista.
 *
 * <p>Nunca añade nada al carrito: sugiere, y la persona decide.
 */
@Service
public class AsistenteCarrito {

    static final String FUNCION = "complementos";

    private static final int POR_PRODUCTO = 8;
    private static final int MAXIMO_CANDIDATOS = 20;
    private static final int SUGERENCIAS = 3;

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;
    private final RecomendacionService recomendaciones;

    public AsistenteCarrito(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos,
            RecomendacionService recomendaciones) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
        this.recomendaciones = recomendaciones;
    }

    public Optional<Complementos> complementos(List<Long> enCarrito) {
        if (!ia.disponible() || enCarrito.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> dentro = new LinkedHashSet<>(enCarrito);
        List<ProductoResponse> carrito = productos.porIds(List.copyOf(dentro));
        if (carrito.isEmpty()) {
            return Optional.empty();
        }

        Map<Long, ProductoResponse> candidatos = new LinkedHashMap<>();
        for (ProductoResponse p : carrito) {
            // Sin sujeto: esto no se anota como recomendación servida ni crea perfil.
            // `similares` devuelve null —no una lista vacía— cuando el producto no
            // tiene relacionados; sin esta comprobación el carrito daba un 500.
            Carrusel relacionados = recomendaciones.similares(p.id(), null, POR_PRODUCTO);
            if (relacionados == null) {
                continue;
            }
            for (ProductoResponse c : relacionados.items()) {
                if (!dentro.contains(c.id()) && c.stock() != null && c.stock() > 0
                        && candidatos.size() < MAXIMO_CANDIDATOS) {
                    candidatos.putIfAbsent(c.id(), c);
                }
            }
        }
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }

        String sistema = """
                Ayudas a completar una compra en SmartZone, tienda de tecnología de Perú.
                Tienes lo que el cliente lleva en el carrito y una lista de candidatos del catálogo.
                Elige hasta %d candidatos que COMPLEMENTEN lo que lleva (accesorios, periféricos, \
                algo que necesitará para usarlo). NO elijas productos que sustituyen a lo que ya \
                lleva (otra laptop si ya lleva una). Si ninguno complementa de verdad, lista vacía.
                Para cada uno, un motivo de máximo 12 palabras, en español, sin markdown.
                %s

                Responde SOLO JSON: {"sugerencias": [{"id": id, "motivo": "..."}]}
                """.formatted(SUGERENCIAS, GuardiaPrompt.REGLA_DATOS);
        String usuario = guardia.delimitar("carrito", carrito.stream()
                        .map(p -> "- " + p.name() + " (" + p.categoriaName() + ")")
                        .collect(Collectors.joining("\n")))
                + "\n\n" + guardia.delimitar("candidatos", candidatos.values().stream()
                        .map(p -> "[" + p.id() + "] " + p.name() + " (" + p.categoriaName() + ") S/ "
                                + p.precioActual().toPlainString())
                        .collect(Collectors.joining("\n")));

        return ia.json(FUNCION, sistema, usuario, 300).map(r -> elegir(r, candidatos));
    }

    static Complementos elegir(JsonNode r, Map<Long, ProductoResponse> candidatos) {
        Map<Long, String> motivos = new LinkedHashMap<>();
        for (JsonNode s : r.path("sugerencias")) {
            JsonNode nodo = s.path("id");
            Long id = nodo.isIntegralNumber() ? Long.valueOf(nodo.asLong())
                    : nodo.isString() && nodo.asString().matches("\\d{1,18}") ? Long.valueOf(nodo.asString())
                    : null;
            if (id != null && candidatos.containsKey(id) && motivos.size() < SUGERENCIAS) {
                motivos.putIfAbsent(id, Respuestas.texto(s, "motivo", 100));
            }
        }
        return new Complementos(motivos.keySet().stream().map(candidatos::get).toList(), motivos);
    }
}
