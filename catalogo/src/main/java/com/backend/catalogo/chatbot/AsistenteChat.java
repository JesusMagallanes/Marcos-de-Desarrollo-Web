package com.backend.catalogo.chatbot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.chatbot.dto.ChatbotDtos.RespuestaChat;
import com.backend.catalogo.chatbot.dto.ChatbotDtos.TurnoChat;
import com.backend.catalogo.ia.InterpreteBusqueda;
import com.backend.catalogo.ia.InterpreteBusqueda.Intencion;
import com.backend.catalogo.ia.InterpreteBusqueda.Interpretacion;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.compras.ComprasClient;
import com.backend.catalogo.shared.compras.ComprasClient.PedidosDelCliente;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El chat con IA, anclado al catálogo de verdad.
 *
 * <p>El chatbot de la rama {@code mobile} mandaba la pregunta al modelo con unos
 * datos fijos de la tienda escritos en el prompt. Contestaba bien sobre envíos,
 * pero de productos solo podía inventar: no sabía qué había, a qué precio ni con
 * qué stock. Aquí el modelo nunca ve el catálogo entero ni lo imagina:
 *
 * <ol>
 *   <li>{@link InterpreteBusqueda} convierte el mensaje —con la conversación
 *       previa, para entender «¿y en 16 GB?»— en intención y filtros;</li>
 *   <li>el servidor busca con esos filtros en la base, como cualquier búsqueda,
 *       o consulta los pedidos de quien pregunta si eso es lo que quiere;</li>
 *   <li>el modelo redacta la respuesta con ESOS datos delante y elige qué
 *       recomendar, por id;</li>
 *   <li>los ids se cruzan con la lista: un producto que no estaba se descarta.
 *       Las tarjetas que ve el cliente salen de la base, no del texto del modelo,
 *       así que precio y stock son siempre los reales.</li>
 * </ol>
 *
 * <h4>Memoria de la conversación</h4>
 *
 * <p>La lleva el navegador y la manda con cada mensaje; el servidor no guarda
 * chats. Como la escribe el cliente, se trata como datos de terceros: se limpia
 * turno a turno, un turno sospechoso se descarta y todo va delimitado.
 *
 * <h4>Pedidos</h4>
 *
 * <p>Solo con sesión, y con el token de la propia persona: {@code compras}
 * aplica sus permisos, así que el chat no puede ver pedidos ajenos. Al modelo le
 * llega lo imprescindible para contestar —número, fecha, estado, productos,
 * total, estado del envío y distrito—; nunca la dirección, el teléfono ni quién
 * recibe.
 *
 * <p>Si algo de eso falla, {@link ChatbotService} contesta con sus reglas de
 * siempre. El cliente ve una respuesta menos elaborada, nunca un error.
 */
@Service
public class AsistenteChat {

    public static final String FUNCION = "chat";

    private static final int CANDIDATOS = 8;
    private static final int RECOMENDADOS = 4;
    private static final int LONGITUD_RESPUESTA = 700;
    private static final int PEDIDOS_RECIENTES = 5;

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final InterpreteBusqueda interprete;
    private final ProductoService productos;
    private final ComprasClient compras;
    private final JsonMapper json;

    public AsistenteChat(ClienteIa ia, GuardiaPrompt guardia, InterpreteBusqueda interprete,
            ProductoService productos, ComprasClient compras, JsonMapper json) {
        this.ia = ia;
        this.guardia = guardia;
        this.interprete = interprete;
        this.productos = productos;
        this.compras = compras;
        this.json = json;
    }

    public Optional<RespuestaChat> responder(String mensaje) {
        return responder(mensaje, List.of(), null);
    }

    /**
     * @param historial turnos anteriores, del más antiguo al más reciente
     * @param token JWT de quien pregunta, o null si no hay sesión
     */
    public Optional<RespuestaChat> responder(String mensaje, List<TurnoChat> historial, String token) {
        GuardiaPrompt.Entrada entrada = guardia.limpiar(mensaje, 500);
        if (!ia.disponible() || entrada.vacia()) {
            return Optional.empty();
        }
        if (entrada.sospechosa()) {
            // No se gasta una llamada en un intento de manipular al modelo, y la
            // respuesta no confirma qué se detectó.
            return Optional.of(new RespuestaChat(
                    "Puedo ayudarte con productos, ofertas, pedidos, envíos, pagos y contacto de "
                            + "<b>SmartZone</b>. ¿Qué estás buscando?",
                    "ia", List.of(), null));
        }

        String conversacion = conversacion(historial);
        Optional<Interpretacion> interpretacion = interprete.interpretar(entrada.texto(), conversacion);
        if (interpretacion.isEmpty()) {
            return Optional.empty();
        }
        Interpretacion i = interpretacion.get();

        if (i.intencion() == Intencion.PEDIDO) {
            return Optional.of(sobrePedidos(entrada.texto(), conversacion, token));
        }

        List<ProductoResponse> candidatos = candidatos(i);
        return ia.json(FUNCION, sistema(), usuario(entrada.texto(), conversacion, candidatos), 450)
                .flatMap(r -> armar(r, candidatos, i));
    }

    /* ══════════════ Productos ══════════════ */

    private List<ProductoResponse> candidatos(Interpretacion i) {
        return switch (i.intencion()) {
            case PRODUCTOS -> productos.buscar(i.busqueda().texto(), i.busqueda().categoriaSlug(),
                    i.filtro(), 0, CANDIDATOS).content();
            case OFERTAS -> productos.ofertas(CANDIDATOS);
            default -> List.of();
        };
    }

    Optional<RespuestaChat> armar(JsonNode respuesta, List<ProductoResponse> candidatos,
            Interpretacion i) {
        String texto = respuesta.path("respuesta").asString("").trim();
        if (texto.isEmpty()) {
            return Optional.empty();
        }
        if (texto.length() > LONGITUD_RESPUESTA) {
            texto = texto.substring(0, LONGITUD_RESPUESTA) + "…";
        }

        Map<Long, ProductoResponse> porId = candidatos.stream()
                .collect(Collectors.toMap(ProductoResponse::id, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        Set<Long> elegidos = new LinkedHashSet<>();
        for (JsonNode nodo : respuesta.path("productos")) {
            // Los modelos devuelven ids como número o como texto; los dos valen.
            Long id = nodo.isIntegralNumber() ? Long.valueOf(nodo.asLong())
                    : nodo.isString() && nodo.asString().matches("\\d{1,18}") ? Long.valueOf(nodo.asString())
                    : null;
            if (id != null && porId.containsKey(id) && elegidos.size() < RECOMENDADOS) {
                elegidos.add(id);
            }
        }
        List<ProductoResponse> tarjetas = new ArrayList<>();
        elegidos.forEach(id -> tarjetas.add(porId.get(id)));

        String categoria = i.intencion() == Intencion.PRODUCTOS ? i.busqueda().categoriaSlug() : null;
        return Optional.of(new RespuestaChat(html(texto), "ia", tarjetas, categoria));
    }

    private String sistema() {
        return """
                Eres el asistente de compras de SmartZone, una tienda de tecnología de Perú.
                Contestas en español, en tono cercano y profesional, en 1 a 4 frases.

                DATOS DE LA TIENDA (son los únicos que puedes afirmar):
                %s

                Recibes el mensaje del cliente, la conversación previa si la hay, y una lista
                de productos del catálogo real.
                Reglas:
                - Solo recomienda productos de la lista. Nunca inventes productos, precios,
                  stock, garantías ni promociones que no estén en los datos.
                - Precios en soles con "S/". Si "disponible" es false, dilo.
                - Si la lista está vacía y buscan un producto, dilo con honestidad y sugiere
                  ampliar la búsqueda (otra marca, más presupuesto, otra categoría).
                - Si preguntan algo que no es de la tienda, redirige amablemente.
                - No uses markdown ni HTML.
                %s

                Responde SOLO un objeto JSON:
                {"respuesta": "texto para el cliente", "productos": [ids de la lista, en orden de recomendación, máximo 4]}
                """.formatted(ChatbotService.informacionTiendaEnTexto(), GuardiaPrompt.REGLA_DATOS);
    }

    private String usuario(String mensaje, String conversacion, List<ProductoResponse> candidatos) {
        List<Map<String, Object>> lista = candidatos.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("nombre", p.name());
            m.put("marca", p.marcaName());
            m.put("categoria", p.categoriaName());
            m.put("precio", p.precioActual());
            if (p.enOferta()) {
                m.put("precioAntes", p.precio());
            }
            m.put("disponible", p.stock() != null && p.stock() > 0);
            if (p.calificacionPromedio() != null) {
                m.put("valoracion", Math.round(p.calificacionPromedio() * 10) / 10.0);
            }
            String descripcion = p.description() == null ? "" : p.description();
            m.put("descripcion", descripcion.length() > 160 ? descripcion.substring(0, 160) : descripcion);
            return m;
        }).toList();

        return previa(conversacion)
                + guardia.delimitar("catalogo", json.writeValueAsString(lista)) + "\n\n"
                + guardia.delimitar("mensaje_cliente", mensaje);
    }

    /* ══════════════ Pedidos ══════════════ */

    private RespuestaChat sobrePedidos(String mensaje, String conversacion, String token) {
        if (token == null) {
            return new RespuestaChat("Para consultar tus pedidos necesito que <b>inicies sesión</b>. "
                    + "Después podrás preguntarme cuándo llega tu compra.", "ia", List.of(), null);
        }
        Optional<PedidosDelCliente> datos = compras.pedidosDe(token);
        if (datos.isEmpty()) {
            return new RespuestaChat("Ahora mismo no puedo consultar tus pedidos. Puedes verlos en "
                    + "<b>Mis compras</b>.", "ia", List.of(), null);
        }
        List<Map<String, Object>> resumen = resumenPedidos(datos.get());
        if (resumen.isEmpty()) {
            return new RespuestaChat("Todavía no tienes pedidos en SmartZone. ¿Te ayudo a encontrar algo?",
                    "ia", List.of(), null);
        }

        String sistema = """
                Eres el asistente de SmartZone. El cliente pregunta por sus pedidos y tienes
                sus pedidos reales más recientes. Contesta en español, en 1 a 3 frases, solo
                con esos datos: número de pedido, estado, fechas y productos. Si no puedes
                saber algo (una fecha de entrega que no consta), dilo y sugiere "Mis compras".
                Estados: PENDIENTE = pago sin completar; CONFIRMADO = contra entrega, en preparación;
                PAGADO = pagado, en preparación; EN_TRANSITO = en camino; ENTREGADO; CANCELADO.
                No uses markdown ni HTML.
                %s

                Responde SOLO JSON: {"respuesta": "texto para el cliente"}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = previa(conversacion)
                + guardia.delimitar("pedidos", json.writeValueAsString(resumen)) + "\n\n"
                + guardia.delimitar("mensaje_cliente", mensaje);

        return ia.json(FUNCION, sistema, usuario, 300)
                .map(r -> r.path("respuesta").asString("").trim())
                .filter(t -> !t.isEmpty())
                .map(t -> new RespuestaChat(html(t.length() > LONGITUD_RESPUESTA
                        ? t.substring(0, LONGITUD_RESPUESTA) + "…" : t), "ia", List.of(), null))
                .orElseGet(() -> new RespuestaChat("Puedes ver el estado de todos tus pedidos en "
                        + "<b>Mis compras</b>.", "ia", List.of(), null));
    }

    /**
     * Lo mínimo de cada pedido: sin dirección, teléfono ni receptor.
     *
     * <p>Se construye campo a campo a propósito, sobre la respuesta cruda de
     * {@code compras}: si mañana esa respuesta gana un campo con datos
     * personales, no llega al modelo sin que alguien lo añada aquí.
     */
    List<Map<String, Object>> resumenPedidos(PedidosDelCliente datos) {
        Map<Object, Map<String, Object>> envioPorPedido = new LinkedHashMap<>();
        for (Map<String, Object> e : datos.envios()) {
            envioPorPedido.put(String.valueOf(e.get("pedidoId")), e);
        }
        return datos.pedidos().stream()
                .limit(PEDIDOS_RECIENTES)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("numero", p.get("numero"));
                    m.put("fecha", p.get("fecha"));
                    m.put("estado", p.get("estado"));
                    m.put("total", p.get("total"));
                    Object detalles = p.get("detalles");
                    if (detalles instanceof List<?> lista) {
                        m.put("productos", lista.stream()
                                .filter(Map.class::isInstance)
                                .map(d -> ((Map<?, ?>) d).get("cantidad") + " x " + ((Map<?, ?>) d).get("productoNombre"))
                                .toList());
                    }
                    Map<String, Object> envio = envioPorPedido.get(String.valueOf(p.get("id")));
                    if (envio != null) {
                        m.put("envio", envio.get("estadoEnvio"));
                        m.put("envioProgramado", envio.get("fechaEnvioProgramado"));
                        m.put("entregado", envio.get("fechaEnvioEntregado"));
                        m.put("distrito", envio.get("distrito"));
                    }
                    return m;
                })
                .toList();
    }

    /* ══════════════ Piezas ══════════════ */

    /**
     * Los turnos previos, limpios y en texto: «cliente: …» / «asistente: …».
     * Un turno que intenta manipular al modelo se descarta entero.
     */
    String conversacion(List<TurnoChat> historial) {
        if (historial == null || historial.isEmpty()) {
            return null;
        }
        String texto = historial.stream()
                .skip(Math.max(0, historial.size() - 6))
                .map(t -> new Object[] { t.autor(), guardia.limpiar(t.texto(), 400) })
                .filter(par -> !((GuardiaPrompt.Entrada) par[1]).sospechosa()
                        && !((GuardiaPrompt.Entrada) par[1]).vacia())
                .map(par -> ("asistente".equals(par[0]) ? "asistente: " : "cliente: ")
                        + ((GuardiaPrompt.Entrada) par[1]).texto())
                .collect(Collectors.joining("\n"));
        return texto.isBlank() ? null : texto;
    }

    private String previa(String conversacion) {
        return conversacion == null ? "" : guardia.delimitar("conversacion_previa", conversacion) + "\n\n";
    }

    /** El chat del frontend pinta HTML: todo lo que escribe el modelo se escapa. */
    private static String html(String texto) {
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }
}
