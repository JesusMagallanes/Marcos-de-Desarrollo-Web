package com.backend.web_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.AssertionErrors;
import org.springframework.util.AntPathMatcher;

/**
 * Que toda familia de rutas de la API tenga un destino.
 *
 * <p>Esta clase nace de un olvido silencioso: {@code /api/sync/**} no estaba
 * enrutado. Una ruta que falta no da un error claro —el gateway responde con el
 * index.html de la SPA para cualquier cosa que no reconozca—, así que el
 * navegador recibe un 200 con HTML donde esperaba JSON. La cola de
 * sincronización sin conexión llevaba así desde que se añadió.
 *
 * <p>Se lee el fichero de rutas en vez de levantar el contexto porque lo que se
 * quiere comprobar es justo lo que está DECLARADO, sin necesitar los tres
 * servicios en pie ni un {@code .env}.
 */
@DisplayName("Rutas del gateway")
class RutasConfigTest {

    private static final Path FUENTE = Path.of(
            "src/main/java/com/backend/web_gateway/config/RutasConfig.java");

    /** Los `path("...")` declarados en la configuración. */
    private static List<String> patronesDeclarados() {
        String codigo;
        try {
            codigo = Files.readString(FUENTE);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo leer " + FUENTE, ex);
        }

        Matcher coincidencias = Pattern.compile("path\\(\"([^\"]+)\"\\)").matcher(codigo);
        List<String> patrones = coincidencias.results()
                .map(r -> r.group(1))
                .collect(Collectors.toList());

        AssertionErrors.assertTrue("No se encontró ninguna ruta declarada", !patrones.isEmpty());
        return patrones;
    }

    private static boolean estaEnrutada(String url) {
        AntPathMatcher matcher = new AntPathMatcher();
        return patronesDeclarados().stream().anyMatch(patron -> matcher.match(patron, url));
    }

    /**
     * Una URL por familia de la API que el navegador llama de verdad.
     *
     * <p>{@code /api/sync/valoraciones} es la que faltaba; el resto están para
     * que si alguien reorganiza el enrutado se note aquí y no en producción.
     */
    @ParameterizedTest(name = "{0} tiene destino")
    @ValueSource(strings = {
            "/api/productos",
            "/api/productos/42",
            "/api/categorias",
            "/api/marcas",
            "/api/guias",
            "/api/chatbot/mensaje",
            "/api/valoraciones/admin",
            "/api/sync/valoraciones",
            "/api/auth/login",
            "/api/usuarios/1",
            "/api/roles",
            "/api/ubigeo/departamentos",
            "/api/colaboradores/solicitudes",
            "/api/carrito",
            "/api/pedidos/mios",
            "/api/metodos-pago",
            "/api/envios/mios",
            "/api/pagos/preferencia",
    })
    void cadaFamiliaDeLaApiTieneDestino(String url) {
        assertThat(estaEnrutada(url))
                .as("«%s» no está enrutada: el gateway devolvería el index.html de la SPA "
                        + "y quien la llame recibiría HTML donde espera JSON", url)
                .isTrue();
    }

    @Test
    @DisplayName("el inventario NO se expone: es contrato interno entre compras y catálogo")
    void elInventarioNoSeExpone() {
        // Si algún día alguien lo añade «para probar», que se entere aquí: desde
        // el navegador se podría mover el stock de la tienda sin comprar nada.
        assertThat(estaEnrutada("/api/inventario/reservar")).isFalse();
    }
}
