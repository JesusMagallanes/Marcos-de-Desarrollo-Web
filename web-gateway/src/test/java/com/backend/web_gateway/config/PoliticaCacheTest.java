package com.backend.web_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Qué puede cachear el navegador y qué no.
 *
 * <p>La regla que se prueba aquí es una sola: <b>una caché eterna solo es
 * segura si el nombre del fichero cambia cuando cambia su contenido</b>. Todo lo
 * demás tiene que poder actualizarse.
 */
@DisplayName("Política de caché de los estáticos")
class PoliticaCacheTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/main-XW7WSHRM.js",
            "/styles-3TCYNE4V.css",
            "/chunk-4BB2JRVF.js",
            "/media/fa-solid-900-K2BNKQXH.woff2",
    })
    @DisplayName("lo que lleva el hash en el nombre se cachea para siempre")
    void loFirmadoEsInmutable(String ruta) {
        assertThat(PoliticaCache.para(ruta)).isEqualTo(PoliticaCache.INMUTABLE);
    }

    @Test
    @DisplayName("index.html NUNCA se cachea")
    void elIndiceNoSeCachea() {
        /*
         * Es el fallo que rompía la aplicación entera tras cada despliegue: con
         * un año de caché, el navegador seguía leyendo un index.html viejo que
         * apuntaba a bundles con hash que ya no existían.
         */
        assertThat(PoliticaCache.para("/index.html")).isEqualTo(PoliticaCache.SIN_CACHE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/carrito", "/perfil/compras", "/admin/productos" })
    @DisplayName("las rutas del router tampoco: lo que se responde ahí es index.html")
    void lasRutasDeAngularNoSeCachean(String ruta) {
        assertThat(PoliticaCache.para(ruta)).isEqualTo(PoliticaCache.SIN_CACHE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/Img/anuncio.png",
            "/Img/Icon-Carrito.svg",
            "/Img/Prueba1.webp",
            "/favicon.ico",
    })
    @DisplayName("los estáticos SIN hash se revalidan: se pueden reemplazar")
    void loNoFirmadoSeRevalida(String ruta) {
        /*
         * Angular copia `public/` tal cual, sin firmar. Con `immutable` —que es
         * literalmente «no vuelvas a preguntar»— cambiar un logo no lo veía
         * nadie nunca, y ese es el otro lado de «las imágenes se dejan de ver».
         */
        assertThat(PoliticaCache.para(ruta)).isEqualTo(PoliticaCache.REVALIDAR);
    }

    @Test
    @DisplayName("una caché eterna nunca se le da a algo que pueda cambiar de contenido")
    void nadaQuePuedaCambiarSeCongelaParaSiempre() {
        // La invariante de toda esta clase, escrita como prueba: si la ruta no
        // lleva firma en el nombre, no puede salir INMUTABLE de aquí.
        String[] sinFirma = {
                "/index.html", "/favicon.ico", "/Img/head.png", "/main.js", "/styles.css",
                "/media/logo.svg", "/carrito", "/",
        };

        for (String ruta : sinFirma) {
            assertThat(PoliticaCache.para(ruta))
                    .as("%s no lleva hash: no puede ser inmutable", ruta)
                    .isNotEqualTo(PoliticaCache.INMUTABLE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/productos",
            "/api/pagos/preferencia",
            "/actuator/health",
            "/oauth2/authorization/google",
            "/login/oauth2/code/google",
    })
    @DisplayName("a la API no se le toca la cabecera: la decide cada servicio")
    void laApiNoEsCosaDeEsteFiltro(String ruta) {
        assertThat(PoliticaCache.para(ruta)).isNull();
    }

    @Test
    @DisplayName("una ruta vacía o nula no revienta")
    void entradasRaras() {
        assertThat(PoliticaCache.para(null)).isNull();
        assertThat(PoliticaCache.para("")).isNull();
    }
}
