package com.backend.catalogo.ia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.ia.dto.IaDtos.Comparacion;
import com.backend.catalogo.ia.dto.IaDtos.ComparacionRequest;
import com.backend.catalogo.ia.dto.IaDtos.FilaComparacion;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.AtributoValor;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.error.DatosInvalidosException;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import tools.jackson.databind.JsonNode;

/**
 * Comparar dos o tres productos, con una explicación para personas.
 *
 * <h4>La tabla es de la base; la explicación, de la IA</h4>
 *
 * <p>Las filas —precio, valoración, disponibilidad y cada característica— salen
 * de {@code producto_atributo}, así que un número de la tabla nunca es una
 * alucinación. El modelo solo redacta qué diferencias importan para el uso que
 * dijo la persona y, si hay uso, cuál encaja mejor; esa recomendación tiene que
 * ser uno de los productos comparados o se descarta. Sin IA la tabla sale igual.
 */
@Service
public class Comparador {

    static final String FUNCION = "comparador";

    /** Lo que aporta el modelo, que es lo único que se guarda: la tabla se rehace. */
    record Veredicto(String explicacion, Long recomendadoId) {
    }

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;
    private final CacheIa cache;

    public Comparador(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos, CacheIa cache) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
        this.cache = cache;
    }

    public Comparacion comparar(ComparacionRequest dto) {
        List<Long> ids = dto.productoIds().stream().distinct().toList();
        if (ids.size() < 2) {
            throw new DatosInvalidosException("Elige al menos dos productos distintos para comparar");
        }
        // `obtener` responde 404 con uno que no existe o no está publicado.
        List<ProductoResponse> lista = ids.stream().map(productos::obtener).toList();
        List<FilaComparacion> filas = filas(lista);

        GuardiaPrompt.Entrada uso = guardia.limpiar(dto.uso(), 200);
        if (!ia.disponible() || uso.sospechosa()) {
            return new Comparacion(lista, filas, null, null);
        }

        String tabla = filas.stream()
                .map(f -> f.caracteristica() + ": " + String.join(" | ", f.valores()))
                .collect(Collectors.joining("\n"));
        String cabecera = lista.stream().map(p -> "[" + p.id() + "] " + p.name())
                .collect(Collectors.joining(" | "));

        String sistema = """
                Ayudas a un cliente de SmartZone (tienda de tecnología de Perú) a elegir entre \
                productos. Tienes una tabla con sus datos reales, en el mismo orden que la cabecera.
                Reglas:
                - Explica en 2 a 4 frases las diferencias que de verdad importan%s. Usa solo los \
                datos de la tabla; si falta un dato para algún producto, no lo supongas.
                - Si hay un uso y un producto encaja claramente mejor, pon su id en recomendadoId; \
                si no está claro o no hay uso, null.
                - Español, sin markdown ni HTML. Precios en S/.
                %s

                Responde SOLO JSON: {"explicacion": "...", "recomendadoId": id o null}
                """.formatted(uso.vacia() ? "" : " para el uso que indica el cliente",
                GuardiaPrompt.REGLA_DATOS);
        String usuario = guardia.delimitar("productos", cabecera + "\n\n" + tabla)
                + (uso.vacia() ? "" : "\n\n" + guardia.delimitar("uso", uso.texto()));

        /*
         * Se guarda el veredicto, no la comparación entera: los precios y el
         * stock se vuelven a leer en cada visita, y la clave lleva la tabla que
         * se envió, así que un cambio de precio invalida lo guardado.
         */
        Veredicto veredicto = cache.recordar(FUNCION, CacheIa.huella(sistema, usuario),
                () -> ia.json(FUNCION, sistema, usuario, 350)
                        .map(r -> new Veredicto(vacioANulo(Respuestas.texto(r, "explicacion", 700)),
                                recomendado(r, ids))))
                .orElse(null);
        return veredicto == null ? new Comparacion(lista, filas, null, null)
                : new Comparacion(lista, filas, veredicto.explicacion(), veredicto.recomendadoId());
    }

    /** Precio, valoración, stock y la unión de características, en orden estable. */
    static List<FilaComparacion> filas(List<ProductoResponse> lista) {
        List<FilaComparacion> filas = new ArrayList<>();
        filas.add(new FilaComparacion("Precio",
                lista.stream().map(p -> "S/ " + p.precioActual().stripTrailingZeros().toPlainString()).toList()));
        filas.add(new FilaComparacion("Marca",
                lista.stream().map(p -> Objects.requireNonNullElse(p.marcaName(), "—")).toList()));
        filas.add(new FilaComparacion("Valoración", lista.stream()
                .map(p -> p.calificacionPromedio() == null ? "Sin reseñas"
                        : String.format("%.1f de 5 (%d)", p.calificacionPromedio(), p.cantidadValoraciones()))
                .toList()));
        filas.add(new FilaComparacion("Disponible",
                lista.stream().map(p -> p.stock() != null && p.stock() > 0 ? "Sí" : "Agotado").toList()));

        Set<String> nombres = new LinkedHashSet<>();
        List<Map<String, String>> porProducto = new ArrayList<>();
        for (ProductoResponse p : lista) {
            Map<String, String> valores = new LinkedHashMap<>();
            for (AtributoValor a : p.atributos()) {
                nombres.add(a.nombre());
                valores.put(a.nombre(), a.valor() + (a.unidad() == null ? "" : " " + a.unidad()));
            }
            porProducto.add(valores);
        }
        for (String nombre : nombres) {
            filas.add(new FilaComparacion(nombre,
                    porProducto.stream().map(v -> v.getOrDefault(nombre, "—")).toList()));
        }
        return filas;
    }

    private static Long recomendado(JsonNode r, List<Long> ids) {
        JsonNode nodo = r.path("recomendadoId");
        Long id = nodo.isIntegralNumber() ? Long.valueOf(nodo.asLong())
                : nodo.isString() && nodo.asString().matches("\\d{1,18}") ? Long.valueOf(nodo.asString())
                : null;
        return id != null && ids.contains(id) ? id : null;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
