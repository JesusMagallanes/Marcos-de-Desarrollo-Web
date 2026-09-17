package com.backend.catalogo.ia;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.backend.catalogo.ia.dto.IaDtos.AyudaResena;
import com.backend.catalogo.ia.dto.IaDtos.AyudaResenaRequest;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

/**
 * Preguntas que ayudan a escribir una reseña útil. Nunca la reseña.
 *
 * <p>«Bueno» no le sirve a nadie, pero la solución no es que la IA escriba la
 * opinión: entonces deja de ser de quien compró, y una tienda llena de reseñas
 * redactadas por un modelo pierde lo único que las hace valiosas. Aquí la IA
 * pregunta —«¿qué tal la batería en un día de uso?»— y la persona contesta con
 * sus palabras. El modelo tiene instrucción de no proponer texto, y lo que
 * devuelve se acota a preguntas cortas.
 */
@Service
public class AyudaResenas {

    static final String FUNCION = "ayuda_resena";

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;

    public AyudaResenas(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
    }

    public Optional<AyudaResena> sugerir(AyudaResenaRequest dto) {
        GuardiaPrompt.Entrada borrador = guardia.limpiar(dto.borrador(), 1500);
        if (!ia.disponible() || borrador.sospechosa()) {
            return Optional.empty();
        }
        ProductoResponse p = productos.obtener(dto.productoId());

        String sistema = """
                Ayudas a un comprador de SmartZone a escribir una reseña útil para otros compradores.
                NO escribas la reseña ni propongas frases para copiar: haz entre 2 y 3 preguntas \
                cortas (máximo 12 palabras cada una) sobre aspectos que su borrador todavía no \
                cubre y que importan para ese tipo de producto (rendimiento, batería, pantalla, \
                calidad de construcción, lo que corresponda). Si da una calificación baja, pregunta \
                qué falló. Tono amable, español, sin markdown.
                %s

                Responde SOLO JSON: {"preguntas": ["...", "..."]}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = "Producto: " + p.name() + " (" + p.categoriaName() + ")\nCalificación: "
                + dto.calificacion() + " de 5\n\n"
                + guardia.delimitar("borrador", borrador.vacia() ? "(todavía vacío)" : borrador.texto());

        return ia.json(FUNCION, sistema, usuario, 200)
                .map(r -> new AyudaResena(Respuestas.lista(r, "preguntas", 3, 110)))
                .filter(a -> !a.preguntas().isEmpty());
    }
}
