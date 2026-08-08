package com.backend.usuarios.shared.seguridad;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/** A05 (Configuración de seguridad defectuosa). */
public final class CabecerasSeguridad {

    private CabecerasSeguridad() {
    }

    /** CSP para respuestas de API: no ejecutan nada, así que se bloquea todo. */
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    public static Customizer<HeadersConfigurer<HttpSecurity>> paraApi() {
        return headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_API))
                // HSTS solo tiene efecto sobre HTTPS; en local es inofensivo y
                // en producción evita el primer salto en claro.
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .permissionsPolicyHeader(pp -> pp
                        .policy("camera=(), microphone=(), geolocation=(), payment=()"));
    }
}
