package com.backend.catalogo.shared.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caché en memoria de las respuestas públicas que son iguales para todo el mundo.
 *
 * <h4>Por qué</h4>
 *
 * <p>La base está en otro país: cada ida y vuelta cuesta ~130 ms y una
 * transacción de una sola consulta, medio segundo. Las categorías, las marcas,
 * las guías y la portada se piden en CADA visita y cambian una vez al día. Sin
 * esto, cada visitante pagaba ese medio segundo por lista, y la portada entera
 * varios segundos, para recibir exactamente lo mismo que el anterior.
 *
 * <h4>Qué se cachea y qué no</h4>
 *
 * <p>Solo lo que no depende de quién pregunta: si una respuesta cambia con el
 * usuario (carrito, pedidos, «mis productos», recomendaciones personales) no
 * pasa por aquí, porque una caché por respuesta le enseñaría a uno lo del otro.
 * Y solo lecturas: las escrituras vacían la entrada que tocan
 * ({@code @CacheEvict} en cada servicio), y por si alguna se escapa —un cambio
 * de stock desde {@code compras}, por ejemplo— las entradas caducan solas a los
 * sesenta segundos. Un minuto de portada vieja es aceptable; un minuto de
 * espera, no.
 *
 * <p>Es por instancia. Con varias réplicas cada una tiene la suya y el mismo
 * dato puede tardar un minuto en igualarse entre ellas, que para esto es
 * indiferente.
 */
@Configuration
@EnableCaching
// Con `spring.cache.type=none` (las pruebas de integración) este bean no se crea
// y Boot pone un gestor que no guarda nada; sin la condición, un bean propio
// ignoraría esa propiedad y las pruebas verían datos de la prueba anterior.
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
public class CacheConfig {

    public static final String CATEGORIAS = "categorias";
    public static final String MARCAS = "marcas";
    public static final String GUIAS = "guias";
    public static final String PORTADA = "portada";
    public static final String SIMILARES = "similares";
    /** Listados de categoría/búsqueda y sus facetas, con la petición entera como clave. */
    public static final String LISTADOS = "listados";
    public static final String VALORACIONES = "valoraciones";

    static final Duration VIDA = Duration.ofSeconds(60);

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager gestor = new CaffeineCacheManager(CATEGORIAS, MARCAS, GUIAS, PORTADA, SIMILARES, LISTADOS, VALORACIONES);
        gestor.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(VIDA)
                .maximumSize(2_000)
                .recordStats());
        return gestor;
    }
}
