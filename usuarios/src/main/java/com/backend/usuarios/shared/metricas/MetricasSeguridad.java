package com.backend.usuarios.shared.metricas;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/** Métricas de seguridad, expuestas en /actuator/prometheus. */
@Component
public class MetricasSeguridad {

    private final MeterRegistry registro;
    private final AtomicInteger sesionesActivas = new AtomicInteger();

    public MetricasSeguridad(MeterRegistry registro, LimitadorPeticiones limitador) {
        this.registro = registro;

        Gauge.builder(Metricas.SESIONES_ACTIVAS, sesionesActivas, AtomicInteger::get)
                .description("Tokens de acceso emitidos y aún no revocados")
                .register(registro);

        Gauge.builder(Metricas.RATE_LIMIT_CLAVES, limitador, LimitadorPeticiones::clavesActivas)
                .description("Claves distintas que el limitador está siguiendo")
                .register(registro);
    }

    /* ── Autenticación · A07 ── */

    public void loginCorrecto(String rol) {
        contador(Metricas.AUTENTICACION, "Intentos de autenticación",
                Tags.of("resultado", "correcto", "motivo", "ninguno", "rol", rol)).increment();
    }

    /** @param motivo categoría fija: credenciales · cuenta_social · token_invalido. */
    public void loginFallido(String motivo) {
        contador(Metricas.AUTENTICACION, "Intentos de autenticación",
                Tags.of("resultado", "fallido", "motivo", motivo, "rol", "desconocido")).increment();
    }

    public void registroCorrecto() {
        contador(Metricas.AUTENTICACION, "Intentos de autenticación",
                Tags.of("resultado", "registro", "motivo", "ninguno", "rol", "CLIENTE")).increment();
    }

    /* ── Tokens ── */

    public void tokenEmitido() {
        contador(Metricas.TOKEN, "Ciclo de vida de los tokens",
                Tags.of("evento", "emitido", "motivo", "ninguno")).increment();
        sesionesActivas.incrementAndGet();
    }

    /** @param motivo ROTACION · LOGOUT · CAMBIO_ROL. */
    public void tokenRevocado(String motivo) {
        contador(Metricas.TOKEN, "Ciclo de vida de los tokens",
                Tags.of("evento", "revocado", "motivo", motivo)).increment();
        sesionesActivas.updateAndGet(n -> Math.max(0, n - 1));
    }

    /** @param motivo firma · expirado · emisor · audiencia · ausente_o_invalido. */
    public void tokenInvalido(String motivo) {
        contador(Metricas.TOKEN, "Ciclo de vida de los tokens",
                Tags.of("evento", "invalido", "motivo", motivo)).increment();
    }

    /* ── Autorización · A01 ── */

    /** @param recurso nombre del recurso, nunca su id. */
    public void accesoDenegado(String recurso) {
        contador(Metricas.AUTORIZACION_DENEGADA, "Peticiones rechazadas por falta de permisos",
                Tags.of("recurso", recurso)).increment();
    }

    public void cambioRol(String rolNuevo) {
        contador(Metricas.CAMBIO_ROL, "Cambios de rol efectuados",
                Tags.of("rol", rolNuevo)).increment();
    }

    /* ── Abuso · A04 ── */

    public void rateLimitBloqueado(String ambito) {
        contador(Metricas.RATE_LIMIT, "Peticiones rechazadas por exceder el cupo",
                Tags.of("ambito", ambito)).increment();
    }

    /* ── Entrada · A03 ── */

    public void entradaRechazada(String tipo) {
        contador(Metricas.ENTRADA_RECHAZADA, "Peticiones rechazadas por validación",
                Tags.of("tipo", tipo)).increment();
    }

    /* ── Integridad · A08 ── */

    /**
     * Reúso de un refresh token ya canjeado: o se filtró, o alguien lo está reproduciendo.
     * Cualquier valor distinto de cero merece revisión.
     */
    public void reusoRefreshDetectado() {
        integridad("reuso_refresh");
    }

    public void integridad(String evento) {
        contador(Metricas.INTEGRIDAD, "Eventos que delatan manipulación",
                Tags.of("evento", evento)).increment();
    }

    private Counter contador(String nombre, String descripcion, Tags etiquetas) {
        return Counter.builder(nombre)
                .description(descripcion)
                .tags(etiquetas)
                .register(registro);
    }
}
