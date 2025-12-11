package Pry_01.Web.de.Ventas.de.Computadoras.Configuration.Jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
    private final String secret;
    private final long expCliente;
    private final long expEmpleado;
    private final long expAdministrador;
    private final long refreshExp;

    private final Key key;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration.cliente}") long expCliente,
            @Value("${jwt.expiration.empleado}") long expEmpleado,
            @Value("${jwt.expiration.administrador}") long expAdministrador,
            @Value("${jwt.refreshExpiration}") long refreshExp) {
        this.secret = secret;
        this.expCliente = expCliente;
        this.expEmpleado = expEmpleado;
        this.expAdministrador = expAdministrador;
        this.refreshExp = refreshExp;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String username, Roles role) {
        long now = System.currentTimeMillis();
        long exp = switch (role) {
            case CLIENTE -> expCliente;
            case EMPLEADO -> expEmpleado;
            case ADMINISTRADOR -> expAdministrador;
        };
        Date expiry = new Date(now + exp);

        return Jwts.builder()
                .setSubject(username)
                .claim("ROLE_", role.name())
                .setIssuedAt(new Date(now))
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        Date expiry = new Date(now + refreshExp);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // token inválido/expirado
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getRole(String token) {
        Object r = getClaims(token).get("ROLE_");
        return r != null ? r.toString() : null;
    }

    public boolean hasRole(String token, Roles expected) {
        return expected.name().equals(getRole(token));
    }
}
