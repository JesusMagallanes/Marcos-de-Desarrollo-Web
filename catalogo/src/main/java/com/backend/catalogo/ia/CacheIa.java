package com.backend.catalogo.ia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Lo que el modelo ya contestó a la misma pregunta sobre los mismos datos.
 *
 * <h4>Por qué, si cada llamada es distinta</h4>
 *
 * <p>No lo son. Dos clientes comparan las dos laptops que están en la portada;
 * el mismo carrito se abre cinco veces antes de pagar; «¿trae cargador?» es la
 * pregunta de siempre en la misma ficha. Cada una de esas repeticiones costaba
 * dinero y un par de segundos de espera. El resumen de reseñas ya se guardaba
 * así ({@link ResumenResenasService}); esto es lo mismo para el resto.
 *
 * <h4>La clave lleva una huella de lo que se envió</h4>
 *
 * <p>Guardar por «producto 7» serviría precios viejos cuando cambie el precio.
 * Por eso quien llama mete en la clave una huella de los datos que fueron al
 * prompt: si cambia el precio, el stock o las reseñas, la clave es otra y se
 * vuelve a preguntar. Además hay caducidad, que cubre lo que la huella no mira.
 *
 * <p>Es de memoria y por instancia, como el resto de las cachés del proyecto: al
 * reiniciar se regenera sola y con varias réplicas cada una tiene la suya, que
 * para ahorrar llamadas es suficiente.
 */
@Component
public class CacheIa {

    /** Corto a propósito: ahorra la ráfaga de repeticiones, no congela la tienda. */
    private static final Duration VIDA = Duration.ofMinutes(15);

    /**
     * Tope de entradas. Sin él, una ficha popular con mil preguntas distintas
     * haría crecer el mapa sin final; al llegar al tope se vacía entero, que es
     * lo más simple que no deja el servicio sin memoria.
     */
    private static final int TOPE = 2_000;

    private record Guardado(long expiraEn, Object valor) {
    }

    private final Map<String, Guardado> entradas = new ConcurrentHashMap<>();
    private final MeterRegistry registro;

    public CacheIa(MeterRegistry registro) {
        this.registro = registro;
    }

    /**
     * Lo guardado, o lo que devuelva {@code calcular}. Un resultado vacío no se
     * guarda: si el modelo falló, la siguiente visita debe poder intentarlo.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> recordar(String funcion, String clave, Supplier<Optional<T>> calcular) {
        String completa = funcion + '|' + clave;
        Guardado guardado = entradas.get(completa);
        if (guardado != null && System.currentTimeMillis() < guardado.expiraEn()) {
            contar(funcion, "acierto");
            return Optional.of((T) guardado.valor());
        }

        contar(funcion, "fallo");
        Optional<T> calculado = calcular.get();
        calculado.ifPresent(valor -> {
            if (entradas.size() >= TOPE) {
                entradas.clear();
            }
            entradas.put(completa, new Guardado(System.currentTimeMillis() + VIDA.toMillis(), valor));
        });
        return calculado;
    }

    /**
     * La huella de lo que se le envió al modelo, que es la clave natural: si
     * cambia una coma del prompt —un precio, una reseña, la pregunta— cambia la
     * clave. SHA-256 recortado: con un {@code hashCode} dos prompts distintos
     * podrían chocar, y aquí una colisión es contestar otra cosa.
     */
    public static String huella(String... partes) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String parte : partes) {
                sha.update(parte == null ? new byte[0] : parte.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return HexFormat.of().formatHex(sha.digest(), 0, 16);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException(imposible);
        }
    }

    private void contar(String funcion, String resultado) {
        registro.counter("smartzone_ia_cache_total", "funcion", funcion, "resultado", resultado)
                .increment();
    }
}
