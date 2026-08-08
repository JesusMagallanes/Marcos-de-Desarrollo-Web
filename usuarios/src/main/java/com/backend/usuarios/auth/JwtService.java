package com.backend.usuarios.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.backend.usuarios.shared.security.JwtProperties;
import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

/**
 * Único punto del sistema que EMITE tokens. Los otros tres servicios solo los
 * verifican, y para eso les basta el resource-server (sin jjwt).
 */
@Service
public class JwtService {

    /** Algoritmo FIJADO a HS256. */
    private static final MacAlgorithm ALGORITMO = Jwts.SIG.HS256;

    /** Diferencia los tokens de acceso de los de refresco. */
    public static final String CLAIM_TIPO = "tipo";
    public static final String TIPO_ACCESO = "acceso";
    public static final String TIPO_REFRESH = "refresh";

    private final JwtProperties propiedades;
    private final SecretKey clave;

    public JwtService(JwtProperties propiedades) {
        this.propiedades = propiedades;
        byte[] bytes = propiedades.secreto().getBytes(StandardCharsets.UTF_8);

        // A02: HS256 con una clave corta es trivial de romper por fuerza bruta.
        // Se falla al arrancar en vez de operar con criptografía débil.
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "seguridad.jwt.secreto debe tener al menos 32 bytes para HS256");
        }
        if (esSecretoDeEjemplo(propiedades.secreto())) {
            throw new IllegalStateException(
                    "seguridad.jwt.secreto tiene un valor de ejemplo: define JWT_SECRET con un valor propio");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
    }

    public String generarAccessToken(Usuario usuario) {
        long ahora = System.currentTimeMillis();
        long duracion = duracionMillis(usuario.getRol());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(propiedades.emisor())
                .audience().add(propiedades.audiencia()).and()
                .subject(usuario.getEmailAddress())
                // `uid` evita que los otros servicios tengan que consultar a usuarios
                // solo para saber de quién es un carrito o un pedido.
                .claim("uid", usuario.getId())
                .claim("rol", usuario.getRol().name())
                .claim(CLAIM_TIPO, TIPO_ACCESO)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + duracion))
                .signWith(clave, ALGORITMO)
                .compact();
    }

    public String generarRefreshToken(Usuario usuario) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(propiedades.emisor())
                .audience().add(propiedades.audiencia()).and()
                .subject(usuario.getEmailAddress())
                .claim("uid", usuario.getId())
                .claim(CLAIM_TIPO, TIPO_REFRESH)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + propiedades.expiracionRefresh()))
                .signWith(clave, ALGORITMO)
                .compact();
    }

    /**
     * Valida firma, emisor, audiencia y caducidad. Lanza {@link JwtException} si algo no
     * cuadra: nunca devuelve datos de un token no verificado.
     */
    public Claims verificar(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .requireIssuer(propiedades.emisor())
                .requireAudience(propiedades.audiencia())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public LocalDateTime expiracionDe(Claims claims) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(claims.getExpiration().getTime()), ZoneId.systemDefault());
    }

    public long duracionSegundos(Rol rol) {
        return duracionMillis(rol) / 1000;
    }

    private long duracionMillis(Rol rol) {
        return switch (rol) {
            case CLIENTE -> propiedades.expiracionCliente();
            case EMPLEADO -> propiedades.expiracionEmpleado();
            case ADMINISTRADOR -> propiedades.expiracionAdministrador();
        };
    }

    private boolean esSecretoDeEjemplo(String secreto) {
        String s = secreto.toLowerCase();
        return s.contains("cambia-esto")
                || s.contains("changeme")
                || s.contains("midebescambiar")
                || s.contains("secretomuylargoyprivado");
    }
}
