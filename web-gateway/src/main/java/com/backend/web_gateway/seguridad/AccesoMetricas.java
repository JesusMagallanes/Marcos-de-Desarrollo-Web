package com.backend.web_gateway.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Quién puede leer {@code /actuator/prometheus}.
 *
 * <h4>El problema que resuelve</h4>
 *
 * <p>Las métricas exigen rol de ADMINISTRADOR, y está bien: revelan volumen,
 * errores y, desde la IA aplicada, gasto. Pero un recolector como Prometheus no
 * puede tener rol: los JWT de la tienda caducan en minutos y nadie va a renovar
 * a mano el token de un scrape cada cuarto de hora. El resultado era que las
 * métricas existían y no las recogía nadie, que es como no tenerlas.
 *
 * <p>Con esto hay dos puertas para la misma ruta: una persona administradora con
 * su JWT, o un recolector con el secreto compartido {@code METRICAS_TOKEN} en la
 * cabecera {@code X-Metricas-Token}. Sin ese secreto configurado, todo sigue
 * exactamente como antes.
 *
 * <h4>Por qué una cabecera propia y no {@code Authorization}</h4>
 *
 * <p>Porque el filtro del servidor de recursos intenta decodificar cualquier
 * {@code Bearer} antes de que se decida nada de autorización: un secreto que no
 * es un JWT moriría ahí con un 401 y sin explicación útil.
 *
 * <p>La comparación es en tiempo constante. Un secreto que se compara carácter a
 * carácter se puede adivinar midiendo lo que tarda en fallar.
 */
public final class AccesoMetricas {

    public static final String CABECERA = "X-Metricas-Token";

    private AccesoMetricas() {
    }

    public static AuthorizationManager<RequestAuthorizationContext> conTokenOAdministrador(String token) {
        AuthorizationManager<RequestAuthorizationContext> admin =
                AuthorityAuthorizationManager.hasRole("ADMINISTRADOR");
        String esperado = token == null ? "" : token.trim();

        return (Supplier<? extends Authentication> autenticacion, RequestAuthorizationContext contexto) -> {
            if (!esperado.isEmpty()) {
                String recibido = contexto.getRequest().getHeader(CABECERA);
                if (recibido != null && MessageDigest.isEqual(
                        esperado.getBytes(StandardCharsets.UTF_8),
                        recibido.getBytes(StandardCharsets.UTF_8))) {
                    return new AuthorizationDecision(true);
                }
            }
            return admin.authorize(autenticacion, contexto);
        };
    }
}
