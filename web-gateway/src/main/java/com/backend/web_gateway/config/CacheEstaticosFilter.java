package com.backend.web_gateway.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pone el {@code Cache-Control} que le toca a cada fichero del bundle.
 *
 * <p>La decisión vive en {@link PoliticaCache}, que es una función pura y tiene
 * sus pruebas. Aquí solo se aplica.
 *
 * <p><b>Por qué un filtro y no {@code setCacheControl} en cada resource
 * handler.</b> Haría falta partir el handler de {@link SpaConfig} en tres, cada
 * uno con su patrón y su ubicación, y ese handler tiene además el resolutor que
 * devuelve {@code index.html} para las rutas del router de Angular. Partirlo es
 * fácil de romper de una forma que no da error: un patrón que no case deja de
 * servir un fichero, o peor, se lo lleva el catch-all con la política
 * equivocada. Un filtro decide por la ruta, en un sitio, y no toca cómo se
 * resuelven los ficheros.
 *
 * <p>Se ejecuta antes que el {@code DispatcherServlet}, así que la cabecera ya
 * está puesta cuando el handler de recursos escribe la respuesta. El handler no
 * la pisa: solo escribe {@code Cache-Control} si se le ha configurado uno, y
 * ese es justamente el {@code spring.web.resources.cache.cachecontrol.max-age}
 * que se quitó del {@code application.properties} al añadir esto.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CacheEstaticosFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        String politica = PoliticaCache.para(peticion.getRequestURI());
        if (politica != null) {
            respuesta.setHeader(HttpHeaders.CACHE_CONTROL, politica);
        }

        cadena.doFilter(peticion, respuesta);
    }
}
