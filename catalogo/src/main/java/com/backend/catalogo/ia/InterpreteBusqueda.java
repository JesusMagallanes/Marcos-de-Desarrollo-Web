package com.backend.catalogo.ia;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.ia.dto.IaDtos.BusquedaInterpretada;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.marca.dto.MarcaDtos.MarcaResponse;
import com.backend.catalogo.producto.FiltroProductos;
import com.backend.catalogo.producto.FiltroProductos.Orden;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.FacetaAtributo;
import com.backend.catalogo.producto.dto.ProductoDtos.Facetas;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;
import com.backend.catalogo.shared.validacion.Limites;

import tools.jackson.databind.JsonNode;

/**
 * «Laptop para jugar que no pase de 4000 soles» → filtros de catálogo reales.
 *
 * <h4>El modelo propone, el catálogo decide</h4>
 *
 * <p>El modelo recibe el vocabulario REAL de la tienda —categorías, marcas y
 * atributos con sus valores— y devuelve una propuesta en JSON. Ninguna parte de
 * esa propuesta se usa sin comprobarla: una categoría que no existe se descarta,
 * una marca se busca por nombre y se convierte en su id, un atributo tiene que
 * ser un código conocido con un valor que algún producto tenga. Lo que llega a
 * la consulta es siempre un {@link FiltroProductos} válido, exactamente el mismo
 * que produce el rail de filtros a mano.
 *
 * <h4>Relajar antes que dejar la pantalla vacía</h4>
 *
 * <p>Un modelo tiende a sobre-especificar: mete «gamer» como texto además de la
 * categoría, y la búsqueda por texto no encuentra «gamer» en ningún nombre. Si
 * la propuesta completa no da resultados se quita primero el texto libre y luego
 * los atributos, que son lo más frágil. Categoría, marca y precio no se tocan:
 * son lo que la persona dijo con más claridad.
 */
@Service
public class InterpreteBusqueda {

    public static final String FUNCION = "busqueda";

    private static final Pattern TEXTO_VALIDO = Pattern.compile(Limites.TEXTO_BUSQUEDA);
    private static final Duration VIGENCIA_VOCABULARIO = Duration.ofMinutes(5);
    private static final int VALORES_POR_ATRIBUTO = 15;

    /** Intención de quien escribe; la usa el chat para decidir qué contestar. */
    public enum Intencion {
        PRODUCTOS, OFERTAS, PEDIDO, ENVIO, PAGO, CONTACTO, SALUDO, OTRO;

        static Intencion de(String valor) {
            try {
                return valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException desconocida) {
                return OTRO;
            }
        }
    }

    /** La interpretación completa, con el filtro ya listo para consultar. */
    public record Interpretacion(Intencion intencion, BusquedaInterpretada busqueda,
            FiltroProductos filtro) {
    }

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;
    private final CategoriaService categorias;
    private final MarcaService marcas;

    private volatile Vocabulario vocabulario;

    public InterpreteBusqueda(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos,
            CategoriaService categorias, MarcaService marcas) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
        this.categorias = categorias;
        this.marcas = marcas;
    }

    /**
     * Vacío si no hay IA, si el texto intenta manipular al modelo o si el modelo
     * no devuelve nada utilizable. Quien llama sigue con la búsqueda de siempre.
     */
    public Optional<Interpretacion> interpretar(String consulta) {
        return interpretar(consulta, null);
    }

    /**
     * @param conversacion turnos anteriores del chat, ya limpios, o null. Es lo
     *     que permite entender «¿y en 16 GB?» o «muéstrame una más barata»: los
     *     filtros se heredan de lo que se estaba buscando.
     */
    public Optional<Interpretacion> interpretar(String consulta, String conversacion) {
        GuardiaPrompt.Entrada entrada = guardia.limpiar(consulta, Limites.MAX_BUSQUEDA * 3);
        if (!ia.disponible() || entrada.vacia() || entrada.sospechosa()) {
            return Optional.empty();
        }
        Vocabulario voc = vocabulario();
        String usuario = (conversacion == null || conversacion.isBlank() ? ""
                : guardia.delimitar("conversacion_previa", conversacion) + "\n\n")
                + guardia.delimitar("consulta", entrada.texto());
        return ia.json(FUNCION, sistema(voc), usuario, 400)
                .map(propuesta -> validar(propuesta, voc));
    }

    /* ══════════════ Prompt ══════════════ */

    private String sistema(Vocabulario voc) {
        String atributos = voc.atributos().stream()
                .map(a -> "- " + a.codigo() + " (" + a.nombre() + "): "
                        + a.valores().stream().limit(VALORES_POR_ATRIBUTO)
                                .map(v -> v.valor()).collect(Collectors.joining(" | ")))
                .collect(Collectors.joining("\n"));

        return """
                Eres el motor de búsqueda de SmartZone, una tienda de tecnología de Perú \
                (precios en soles). Conviertes lo que escribe un cliente en filtros de catálogo.

                CATEGORÍAS (slug: nombre):
                %s

                MARCAS:
                %s

                ATRIBUTOS (código (nombre): valores posibles):
                %s

                Responde SOLO un objeto JSON con estas claves:
                {"intencion": "PRODUCTOS|OFERTAS|PEDIDO|ENVIO|PAGO|CONTACTO|SALUDO|OTRO",
                 "categoria": slug o null,
                 "marcas": [nombres exactos de la lista],
                 "precioMin": número o null, "precioMax": número o null,
                 "soloDisponibles": true|false,
                 "orden": "RELEVANCIA|PRECIO_ASC|PRECIO_DESC|NOMBRE_ASC|NOMBRE_DESC|NOVEDAD",
                 "atributos": [{"codigo": "...", "valor": "..."}],
                 "texto": palabras sueltas que no cubra ningún otro campo (un modelo, "rtx 4060") o null}

                Reglas:
                - Usa solo slugs, marcas, códigos y valores de las listas. Si nada encaja, déjalo vacío.
                - "barato", "económico" → orden PRECIO_ASC. "lo mejor", "gama alta" → PRECIO_DESC.
                - "menos de 4000", "hasta 4 mil" → precioMax 4000. "más de 1000" → precioMin 1000.
                - No repitas en "texto" lo que ya está en categoría, marca o atributos.
                - "que haya stock", "para hoy", "disponible" → soloDisponibles true.
                - "mi pedido", "mi compra", "cuándo llega lo que compré" → intencion PEDIDO.
                - Si hay conversación previa y la consulta la continúa ("¿y en 16 GB?", \
                "una más barata"), conserva los filtros de lo que se buscaba y ajusta solo lo nuevo.
                %s
                """.formatted(
                voc.categorias().stream().map(c -> "- " + c.slug() + ": " + c.name())
                        .collect(Collectors.joining("\n")),
                voc.marcas().stream().map(MarcaResponse::name).distinct()
                        .collect(Collectors.joining(", ")),
                atributos.isBlank() ? "(ninguno)" : atributos,
                GuardiaPrompt.REGLA_DATOS);
    }

    /* ══════════════ Validación ══════════════ */

    Interpretacion validar(JsonNode p, Vocabulario voc) {
        Intencion intencion = Intencion.de(p.path("intencion").asString("OTRO"));

        CategoriaResponse categoria = voc.categoriaPorSlug().get(
                p.path("categoria").asString("").trim().toLowerCase(Locale.ROOT));

        List<MarcaResponse> marcasElegidas = new ArrayList<>();
        Set<Long> vistas = new LinkedHashSet<>();
        for (JsonNode nombre : p.path("marcas")) {
            MarcaResponse marca = voc.marcaPorNombre().get(clave(nombre.asString("")));
            if (marca != null && vistas.add(marca.id())) {
                marcasElegidas.add(marca);
            }
        }

        BigDecimal min = precio(p.path("precioMin"));
        BigDecimal max = precio(p.path("precioMax"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal t = min;
            min = max;
            max = t;
        }

        List<String> atributos = new ArrayList<>();
        List<String> etiquetasAtributos = new ArrayList<>();
        for (JsonNode a : p.path("atributos")) {
            FacetaAtributo conocido = voc.atributoPorCodigo().get(a.path("codigo").asString(""));
            if (conocido == null) {
                continue;
            }
            String pedido = clave(a.path("valor").asString(""));
            conocido.valores().stream()
                    .filter(v -> clave(v.valor()).equals(pedido))
                    .findFirst()
                    .ifPresent(v -> {
                        String par = conocido.codigo() + ":" + v.valor();
                        if (!atributos.contains(par)) {
                            atributos.add(par);
                            etiquetasAtributos.add(conocido.nombre() + ": " + v.valor());
                        }
                    });
        }

        String texto = p.path("texto").asString("").trim();
        if (texto.length() > Limites.MAX_BUSQUEDA || !TEXTO_VALIDO.matcher(texto).matches()
                || redundante(texto, categoria, marcasElegidas)) {
            texto = "";
        }

        boolean soloDisponibles = p.path("soloDisponibles").asBoolean(false);
        Orden orden = Orden.de(p.path("orden").asString(null));
        String slug = categoria == null ? null : categoria.slug();
        List<Long> marcaIds = marcasElegidas.stream().map(MarcaResponse::id).toList();

        // Relajar: primero el texto libre, luego los atributos.
        if (!texto.isEmpty() && sinResultados(texto, slug, min, max, marcaIds, atributos, soloDisponibles)) {
            texto = "";
        }
        if (!atributos.isEmpty() && sinResultados(texto, slug, min, max, marcaIds, atributos, soloDisponibles)) {
            atributos.clear();
            etiquetasAtributos.clear();
        }

        FiltroProductos filtro = new FiltroProductos(min, max, marcaIds, List.copyOf(atributos),
                soloDisponibles, orden);
        BusquedaInterpretada busqueda = new BusquedaInterpretada(
                texto.isEmpty() ? null : texto,
                slug,
                categoria == null ? null : categoria.name(),
                marcaIds,
                marcasElegidas.stream().map(MarcaResponse::name).toList(),
                min, max, soloDisponibles,
                orden.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                List.copyOf(atributos),
                resumen(categoria, texto, marcasElegidas, min, max, etiquetasAtributos,
                        soloDisponibles, orden));
        return new Interpretacion(intencion, busqueda, filtro);
    }

    private boolean sinResultados(String texto, String slug, BigDecimal min, BigDecimal max,
            List<Long> marcaIds, List<String> atributos, boolean soloDisponibles) {
        FiltroProductos filtro = new FiltroProductos(min, max, marcaIds, atributos, soloDisponibles,
                Orden.RELEVANCIA);
        return productos.buscar(texto.isEmpty() ? null : texto, slug, filtro, 0, 1).totalElements() == 0;
    }

    private static boolean redundante(String texto, CategoriaResponse categoria,
            List<MarcaResponse> marcas) {
        String t = clave(texto);
        return (categoria != null && clave(categoria.name()).equals(t))
                || marcas.stream().anyMatch(m -> clave(m.name()).equals(t));
    }

    private static BigDecimal precio(JsonNode nodo) {
        if (!nodo.isNumber()) {
            return null;
        }
        BigDecimal valor = nodo.decimalValue();
        return valor.signum() < 0 || valor.compareTo(new BigDecimal("1000000")) > 0 ? null : valor;
    }

    private static String resumen(CategoriaResponse categoria, String texto,
            List<MarcaResponse> marcas, BigDecimal min, BigDecimal max, List<String> atributos,
            boolean soloDisponibles, Orden orden) {
        List<String> partes = new ArrayList<>();
        if (categoria != null) {
            partes.add(categoria.name());
        }
        if (!texto.isEmpty()) {
            partes.add("«" + texto + "»");
        }
        if (!marcas.isEmpty()) {
            partes.add(marcas.stream().map(MarcaResponse::name).collect(Collectors.joining(", ")));
        }
        if (min != null) {
            partes.add("desde S/ " + min.stripTrailingZeros().toPlainString());
        }
        if (max != null) {
            partes.add("hasta S/ " + max.stripTrailingZeros().toPlainString());
        }
        partes.addAll(atributos);
        if (soloDisponibles) {
            partes.add("con stock");
        }
        switch (orden) {
            case PRECIO_ASC -> partes.add("más baratos primero");
            case PRECIO_DESC -> partes.add("más caros primero");
            case NOVEDAD -> partes.add("más nuevos primero");
            default -> {
            }
        }
        return String.join(" · ", partes);
    }

    private static String clave(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    /* ══════════════ Vocabulario de la tienda ══════════════ */

    record Vocabulario(List<CategoriaResponse> categorias, List<MarcaResponse> marcas,
            List<FacetaAtributo> atributos, Instant leido) {

        Map<String, CategoriaResponse> categoriaPorSlug() {
            return categorias.stream().collect(Collectors.toMap(
                    c -> c.slug().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        }

        Map<String, MarcaResponse> marcaPorNombre() {
            return marcas.stream().collect(Collectors.toMap(
                    m -> clave(m.name()), Function.identity(), (a, b) -> a));
        }

        Map<String, FacetaAtributo> atributoPorCodigo() {
            return atributos.stream().collect(Collectors.toMap(
                    FacetaAtributo::codigo, Function.identity(), (a, b) -> a));
        }
    }

    /**
     * Categorías, marcas y atributos, releídos como mucho cada cinco minutos.
     *
     * <p>Son tres consultas que casi nunca cambian; hacerlas en cada búsqueda
     * sería pagar la base por nada. Un alta nueva tarda como mucho cinco minutos
     * en ser «conocida» por la IA, y mientras tanto se sigue encontrando por texto.
     */
    Vocabulario vocabulario() {
        Vocabulario actual = vocabulario;
        if (actual != null && actual.leido().plus(VIGENCIA_VOCABULARIO).isAfter(Instant.now())) {
            return actual;
        }
        Facetas facetas = productos.facetas(null, null,
                new FiltroProductos(null, null, List.of(), List.of(), false, Orden.RELEVANCIA));
        Vocabulario nuevo = new Vocabulario(categorias.listar(), marcas.listar(),
                facetas.atributos(), Instant.now());
        vocabulario = nuevo;
        return nuevo;
    }
}
