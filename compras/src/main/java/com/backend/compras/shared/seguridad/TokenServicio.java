package com.backend.compras.shared.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;

/**
 * Emite un token con el que este servicio se identifica ante los demás.
 *
 * <p><b>Por qué hace falta.</b> Hay trabajo legítimo sin ningún usuario detrás:
 * el barrendero que compensa compras abandonadas y la conciliación de pagos.
 * Esas tareas llaman a {@code catalogo} para liberar o confirmar reservas, y
 * {@code /api/inventario/**} exige estar autenticado.
 *
 * <p>Hasta ahora se llamaba con {@code null} como token, así que <b>la cabecera
 * de autorización simplemente no viajaba y catálogo respondía 401</b>. La
 * compensación fallaba en silencio y se apoyaba en que la reserva caducase sola;
 * para confirmar una venta esa red no existe, porque una reserva caducada libera
 * stock de un pedido ya pagado.
 *
 * <p><b>Por qué se puede firmar aquí.</b> Los cuatro servicios comparten
 * {@code JWT_SECRET} —es la misma clave con la que este valida los tokens que
 * recibe—, así que emitir uno propio no añade ningún secreto nuevo ni ninguna
 * confianza que no existiera ya.
 *
 * <p>El token vive dos minutos y se emite en el momento de usarlo: no se guarda
 * ni se reutiliza. Si se filtrara uno, caduca antes de que sirva de nada.
 */
@Component
@Slf4j
public class TokenServicio {

    /** Lo justo para una llamada entre servicios, con margen de reloj. */
    private static final long VALIDEZ_SEGUNDOS = 120;

    /**
     * Identidad del servicio, no de una persona. Se distingue a propósito para
     * que en una auditoría se vea que la acción no la pidió nadie desde el
     * navegador.
     */
    private static final String SUJETO = "servicio:compras";
    private static final String ROL = "SISTEMA";

    private final byte[] clave;
    private final String emisor;
    private final String audiencia;

    public TokenServicio(@Value("${seguridad.jwt.secreto}") String secreto,
            @Value("${seguridad.jwt.emisor}") String emisor,
            @Value("${seguridad.jwt.audiencia}") String audiencia) {
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.emisor = emisor;
        this.audiencia = audiencia;
    }

    /**
     * Un token nuevo para la llamada que viene.
     *
     * @return el token, o {@code null} si no se pudo firmar; quien llame decide
     *         qué hacer, igual que hacía con el {@code null} de antes
     */
    public String emitir() {
        Instant ahora = Instant.now();

        JWTClaimsSet reclamaciones = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(emisor)
                .audience(audiencia)
                .subject(SUJETO)
                // Sin `uid`: no hay usuario. Las políticas de RLS que filtran por
                // `app.usuario_id` no verán ninguna fila con este token, que es lo
                // correcto: el trabajo interno se marca aparte con ContextoRls.
                .claim("rol", ROL)
                .claim("permisos", List.<String>of())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(VALIDEZ_SEGUNDOS)))
                .build();

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), reclamaciones);
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();

        } catch (Exception ex) {
            // No se propaga: quien llama ya sabe convivir con un token nulo, y
            // tumbar el barrendero por esto dejaría stock bloqueado.
            log.error("No se pudo emitir el token de servicio: {}", ex.getMessage());
            return null;
        }
    }
}
