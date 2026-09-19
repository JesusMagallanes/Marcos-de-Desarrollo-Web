package com.backend.catalogo.ia;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.ia.dto.IaDtos.RespuestaFicha;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.AtributoValor;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;
import com.backend.catalogo.valoracion.ValoracionService;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionResponse;

/**
 * «¿Sirve para editar video?», «¿trae cargador?»: preguntas sobre UN producto,
 * contestadas con su ficha.
 *
 * <h4>Solo lo que dice la ficha</h4>
 *
 * <p>El modelo sabe muchas cosas de muchos portátiles, y ese es el peligro: en
 * una tienda, una respuesta sacada de su memoria sobre «ese modelo» puede ser de
 * otra versión, otro país u otro año, y el cliente compra creyéndola. Por eso la
 * regla es contestar con la ficha, los atributos y las reseñas aprobadas, y
 * nada más: si no consta, se dice que no consta. {@code consta=false} permite
 * al frontend sugerir el contacto en vez de dejar la duda sin salida.
 *
 * <p>Las reseñas entran como opiniones, no como hechos, y sin el nombre de
 * quien las escribió.
 */
@Service
public class AsistenteProducto {

    static final String FUNCION = "pregunta_ficha";
    private static final int RESENAS = 15;

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;
    private final ValoracionService valoraciones;
    private final CacheIa cache;

    public AsistenteProducto(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos,
            ValoracionService valoraciones, CacheIa cache) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
        this.valoraciones = valoraciones;
        this.cache = cache;
    }

    public Optional<RespuestaFicha> preguntar(Long productoId, String pregunta) {
        GuardiaPrompt.Entrada entrada = guardia.limpiar(pregunta, 300);
        if (!ia.disponible() || entrada.vacia()) {
            return Optional.empty();
        }
        if (entrada.sospechosa()) {
            return Optional.of(new RespuestaFicha(
                    "Solo puedo responder preguntas sobre este producto.", false));
        }

        ProductoResponse p = productos.obtener(productoId);
        String atributos = p.atributos().stream()
                .map(AsistenteProducto::linea)
                .collect(Collectors.joining("\n"));
        String resenas = valoraciones.listar(productoId).stream()
                .limit(RESENAS)
                .map(ValoracionResponse::comentario)
                .map(c -> guardia.limpiar(c, 400))
                .filter(e -> !e.sospechosa() && !e.vacia())
                .map(GuardiaPrompt.Entrada::texto)
                .collect(Collectors.joining("\n- ", "- ", ""));

        String ficha = "Nombre: " + p.name()
                + "\nMarca: " + (p.marcaName() == null ? "(no indicada)" : p.marcaName())
                + "\nCategoría: " + p.categoriaName()
                + "\nPrecio: S/ " + p.precioActual().toPlainString()
                + "\nDisponible: " + (p.stock() != null && p.stock() > 0 ? "sí" : "no")
                + "\nDescripción: " + (p.description() == null ? "" : p.description())
                + "\nCaracterísticas:\n" + (atributos.isBlank()
                        ? (p.specifications() == null ? "(ninguna)" : p.specifications())
                        : atributos);

        String sistema = """
                Respondes preguntas de clientes sobre UN producto de SmartZone, tienda de \
                tecnología de Perú. Tienes su ficha y opiniones de compradores.
                Reglas:
                - Responde SOLO con lo que dice la ficha. No uses tu conocimiento general sobre \
                ese modelo: puede ser de otra versión.
                - Si la ficha no lo dice, responde que la ficha no lo indica y pon consta en false. \
                Puedes mencionar lo que opinan compradores, dejando claro que es una opinión.
                - Si preguntan si sirve para un uso, razona con las características que constan y \
                sé prudente.
                - Español, 1 a 3 frases, sin markdown ni HTML. No hables de otros productos.
                %s

                Responde SOLO JSON: {"respuesta": "...", "consta": true|false}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = guardia.delimitar("ficha", ficha) + "\n\n"
                + guardia.delimitar("opiniones", resenas.equals("- ") ? "(sin opiniones)" : resenas) + "\n\n"
                + guardia.delimitar("pregunta", entrada.texto());

        // «¿Trae cargador?» es la pregunta de siempre en la misma ficha. La clave
        // lleva la ficha y las reseñas que se enviaron: si cambian, se repregunta.
        return cache.recordar(FUNCION, CacheIa.huella(sistema, usuario),
                () -> ia.json(FUNCION, sistema, usuario, 300)
                        .map(r -> new RespuestaFicha(Respuestas.texto(r, "respuesta", 600),
                                r.path("consta").asBoolean(false)))
                        .filter(r -> !r.respuesta().isEmpty()));
    }

    private static String linea(AtributoValor a) {
        return "- " + a.nombre() + ": " + a.valor() + (a.unidad() == null ? "" : " " + a.unidad());
    }
}
