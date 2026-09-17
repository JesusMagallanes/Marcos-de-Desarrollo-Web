package com.backend.catalogo.ia;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.ia.dto.IaDtos.ResumenResenas;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;
import com.backend.catalogo.valoracion.ValoracionService;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionResponse;

/**
 * «Lo que dicen los compradores», en tres líneas, en la ficha del producto.
 *
 * <h4>Solo reseñas aprobadas, y sin nombres</h4>
 *
 * <p>Se resumen las que ya pasaron moderación —las mismas que ve cualquiera en la
 * ficha— y al modelo no le llega quién las escribió: solo estrellas y texto. Una
 * reseña que intenta dar órdenes al modelo se deja fuera del resumen.
 *
 * <h4>Se calcula una vez por estado de las reseñas</h4>
 *
 * <p>La ficha es la página más visitada. Llamar al modelo en cada visita sería
 * caro y lento, así que el resumen se guarda con una huella de las reseñas que
 * lo produjeron: mientras no se apruebe, edite o borre ninguna, se sirve el
 * guardado. La caché es de memoria y por instancia: al reiniciar se regenera
 * con la primera visita, que es un coste de una llamada por producto.
 */
@Service
public class ResumenResenasService {

    static final String FUNCION = "resumen_resenas";

    /** Con menos, un «resumen» es repetir una o dos opiniones con otras palabras. */
    static final int MINIMO_RESENAS = 3;
    private static final int MAXIMO_RESENAS = 40;
    private static final int TOPE_CACHE = 2_000;

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ValoracionService valoraciones;

    private record Guardado(int huella, ResumenResenas resumen) {
    }

    private final Map<Long, Guardado> cache = new ConcurrentHashMap<>();

    public ResumenResenasService(ClienteIa ia, GuardiaPrompt guardia, ValoracionService valoraciones) {
        this.ia = ia;
        this.guardia = guardia;
        this.valoraciones = valoraciones;
    }

    /** Vacío sin IA, con pocas reseñas o si el modelo no responde. */
    public Optional<ResumenResenas> resumir(Long productoId) {
        if (!ia.disponible()) {
            return Optional.empty();
        }
        List<ValoracionResponse> todas = valoraciones.listar(productoId);
        List<ValoracionResponse> usables = todas.stream()
                .limit(MAXIMO_RESENAS)
                .filter(v -> !guardia.limpiar(v.comentario(), 600).sospechosa())
                .toList();
        if (usables.size() < MINIMO_RESENAS) {
            return Optional.empty();
        }

        int huella = Objects.hash(usables.stream()
                .map(v -> v.id() + ":" + v.calificacion() + ":" + v.comentario()).toList());
        Guardado guardado = cache.get(productoId);
        if (guardado != null && guardado.huella() == huella) {
            return Optional.of(guardado.resumen());
        }

        String resenas = usables.stream()
                .map(v -> v.calificacion() + "/5 — " + guardia.limpiar(v.comentario(), 600).texto())
                .collect(Collectors.joining("\n"));

        String sistema = """
                Resumes reseñas de compradores de un producto de SmartZone para otros compradores.
                - Sé fiel a lo que dicen: no añadas opiniones ni datos técnicos propios.
                - Si las opiniones están divididas, dilo.
                - Español neutro, sin nombres de personas.
                %s

                Responde SOLO JSON: {"resumen": "2 frases, máximo 280 caracteres", \
                "aFavor": ["máximo 3, muy cortos"], "enContra": ["máximo 3, muy cortos; vacío si no hay"]}
                """.formatted(GuardiaPrompt.REGLA_DATOS);

        Optional<ResumenResenas> resumen = ia.json(FUNCION, sistema,
                guardia.delimitar("resenas", resenas), 350).map(r -> new ResumenResenas(
                        Respuestas.texto(r, "resumen", 320),
                        Respuestas.lista(r, "aFavor", 3, 80),
                        Respuestas.lista(r, "enContra", 3, 80),
                        usables.size()))
                .filter(r -> !r.resumen().isEmpty());

        resumen.ifPresent(r -> {
            if (cache.size() >= TOPE_CACHE) {
                // Tope simple: se vacía antes que crecer sin límite. Se regenera solo.
                cache.clear();
            }
            cache.put(productoId, new Guardado(huella, r));
        });
        return resumen;
    }
}
