package com.backend.catalogo.shared.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.backend.catalogo.shared.metricas.MetricasSeguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Respuestas de 401 y 403 con el mismo formato RFC 7807 que el resto. */
@Component
@RequiredArgsConstructor
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MetricasSeguridad metricas;

    /** 401: falta el token o no es válido. */
    @Override
    public void commence(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            org.springframework.security.core.AuthenticationException ex) throws IOException {

        metricas.tokenInvalido("ausente_o_invalido");
        escribir(respuesta, HttpStatus.UNAUTHORIZED, "No autorizado",
                "Necesitas iniciar sesión para esta operación");
    }

    /** 403: hay sesión, pero el rol no alcanza. */
    @Override
    public void handle(HttpServletRequest peticion,
            HttpServletResponse respuesta,
            org.springframework.security.access.AccessDeniedException ex) throws IOException {

        metricas.accesoDenegado("catalogo");
        escribir(respuesta, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para esta operación");
    }

    private void escribir(HttpServletResponse respuesta, HttpStatus estado,
            String titulo, String detalle) throws IOException {

        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(titulo, estado.value(), detalle));
    }
}
