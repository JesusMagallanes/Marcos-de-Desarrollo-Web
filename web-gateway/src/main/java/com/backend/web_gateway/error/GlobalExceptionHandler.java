package com.backend.web_gateway.error;

import java.net.ConnectException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/** El gateway también necesita su manejador. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** El destino no respondió: no se filtra a qué host se intentó llegar. */
    @ExceptionHandler({ ResourceAccessException.class, ConnectException.class })
    ProblemDetail servicioCaido(Exception ex) {
        log.error("Un servicio de destino no respondió: {}", ex.getMessage());
        return construir(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio no está disponible ahora mismo. Intenta de nuevo en unos segundos.");
    }

    /** Ruta inexistente: 404, no 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rutaNoEncontrada(NoResourceFoundException ex) {
        return construir(HttpStatus.NOT_FOUND, "No encontrado", "La ruta solicitada no existe");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail accesoDenegado(AuthorizationDeniedException ex) {
        return construir(HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para esta operación");
    }

    /**
     * Red de seguridad. El mensaje real solo va al registro; al cliente le llega un texto
     * genérico para no revelar estructura interna (A05).
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Error no controlado en el gateway", ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado");
    }

    private ProblemDetail construir(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
