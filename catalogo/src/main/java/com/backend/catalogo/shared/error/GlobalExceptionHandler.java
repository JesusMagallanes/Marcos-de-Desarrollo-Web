package com.backend.catalogo.shared.error;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.backend.catalogo.shared.metricas.MetricasSeguridad;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Un único formato de error (RFC 7807) para todo el servicio. */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final MetricasSeguridad metricas;

    /* ── Errores de negocio ── */

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException ex) {
        return construir(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    /* ── Validación de entrada (A03) ── */

    /** Cuerpo inválido: se devuelve el detalle por campo, que es seguro y útil. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacionCuerpo(MethodArgumentNotValidException ex) {
        metricas.entradaRechazada("cuerpo");

        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));

        ProblemDetail problema = construir(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Revisa los campos del formulario");
        problema.setProperty("errores", errores);
        return problema;
    }

    /** Parámetro o variable de ruta fuera de los límites de `Limites`. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail validacionParametro(ConstraintViolationException ex) {
        metricas.entradaRechazada("parametro");

        Map<String, String> errores = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String campo = v.getPropertyPath().toString();
            errores.put(campo.substring(campo.lastIndexOf('.') + 1), v.getMessage());
        });

        ProblemDetail problema = construir(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
                "Revisa los parámetros de la petición");
        problema.setProperty("errores", errores);
        return problema;
    }

    /** JSON malformado. No se devuelve el mensaje de Jackson: expone la estructura. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex) {
        metricas.entradaRechazada("json");
        log.debug("Cuerpo ilegible: {}", ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "El cuerpo de la petición no es un JSON válido");
    }

    /** `/api/productos/abc` en vez de un número. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoIncorrecto(MethodArgumentTypeMismatchException ex) {
        metricas.entradaRechazada("tipo");
        return construir(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "El parámetro '" + ex.getName() + "' no tiene el formato esperado");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail parametroFaltante(MissingServletRequestParameterException ex) {
        metricas.entradaRechazada("parametro_faltante");
        return construir(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "Falta el parámetro '" + ex.getParameterName() + "'");
    }

    /* ── Seguridad (A01) ── */

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail accesoDenegado(AuthorizationDeniedException ex) {
        metricas.accesoDenegado("catalogo");
        return construir(HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para esta operación");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail noAutenticado(AuthenticationException ex) {
        metricas.tokenInvalido("rechazado");
        return construir(HttpStatus.UNAUTHORIZED, "No autorizado",
                "Necesitas iniciar sesión para esta operación");
    }

    /* ── Infraestructura ── */

    /** Borrar una categoría con productos asociados cae aquí. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex) {
        // El mensaje de Postgres nombra tablas y restricciones: solo al registro.
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "Conflicto",
                "No se puede completar: el registro tiene elementos asociados");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rutaNoEncontrada(NoResourceFoundException ex) {
        return construir(HttpStatus.NOT_FOUND, "No encontrado", "La ruta solicitada no existe");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "Ese método no está permitido en esta ruta");
    }

    /** Red de seguridad: cualquier fallo no previsto sale genérico. */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado");
    }

    private ProblemDetail construir(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
