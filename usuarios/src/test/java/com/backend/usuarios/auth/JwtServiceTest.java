package com.backend.usuarios.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.usuarios.shared.security.JwtProperties;
import com.backend.usuarios.usuario.RolService;
import com.backend.usuarios.usuario.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * El único sitio del sistema que EMITE tokens.
 *
 * <p>Lo que se prueba aquí no es que la librería de JWT funcione, sino las
 * decisiones propias: qué va dentro del token, qué se exige al verificarlo y qué
 * se rechaza al arrancar. Los otros tres servicios confían en esto sin
 * preguntar, así que un fallo aquí no se nota en `usuarios`: se nota en que
 * catálogo y compras aceptan algo que no debían.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Emisión y verificación de tokens")
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-suficientemente-largo-para-hs256";

    @Mock
    private RolService roles;

    private JwtProperties propiedades;
    private JwtService jwtService;

    @BeforeEach
    void preparar() {
        propiedades = new JwtProperties(
                SECRETO, "smartzone-usuarios", "smartzone-api",
                3_600_000L, 7_200_000L, 1_800_000L, 604_800_000L);
        lenient().when(roles.permisosDe("CLIENTE")).thenReturn(List.of());
        jwtService = new JwtService(propiedades, roles);
    }

    private Usuario usuario(String rol) {
        return Usuario.builder().id(42L).emailAddress("ana@ejemplo.com").rol(rol).build();
    }

    /* ══════════════ Lo que viaja dentro ══════════════ */

    @Test
    @DisplayName("el token de acceso lleva uid, rol y permisos")
    void contenidoDelAcceso() {
        when(roles.permisosDe("EMPLEADO")).thenReturn(List.of("ENVIOS_GESTIONAR"));

        Claims claims = jwtService.verificar(jwtService.generarAccessToken(usuario("EMPLEADO")));

        // `uid` existe para que catálogo y compras sepan de quién es un carrito
        // sin tener que preguntarle a este servicio en cada petición.
        assertThat(claims.get("uid", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.get("rol", String.class)).isEqualTo("EMPLEADO");
        assertThat(claims.get("permisos", List.class)).containsExactly("ENVIOS_GESTIONAR");
        assertThat(claims.get(JwtService.CLAIM_TIPO, String.class)).isEqualTo(JwtService.TIPO_ACCESO);
        assertThat(claims.getSubject()).isEqualTo("ana@ejemplo.com");
    }

    @Test
    @DisplayName("el de refresco NO lleva permisos: solo sirve para canjear")
    void elRefrescoNoLlevaPermisos() {
        Claims claims = jwtService.verificar(jwtService.generarRefreshToken(usuario("CLIENTE")));

        assertThat(claims.get(JwtService.CLAIM_TIPO, String.class)).isEqualTo(JwtService.TIPO_REFRESH);
        assertThat(claims.get("permisos")).isNull();
        assertThat(claims.get("rol")).isNull();
    }

    @Test
    @DisplayName("cada token tiene un jti propio, que es lo que permite revocarlo")
    void jtiUnico() {
        String uno = jwtService.verificar(jwtService.generarRefreshToken(usuario("CLIENTE"))).getId();
        String otro = jwtService.verificar(jwtService.generarRefreshToken(usuario("CLIENTE"))).getId();

        assertThat(uno).isNotNull().isNotEqualTo(otro);
    }

    /* ══════════════ Lo que se rechaza ══════════════ */

    @Test
    @DisplayName("un token firmado con otra clave no pasa")
    void firmaAjena() {
        String ajeno = Jwts.builder()
                .subject("ana@ejemplo.com")
                .issuer("smartzone-usuarios")
                .audience().add("smartzone-api").and()
                .signWith(Keys.hmacShaKeyFor(
                        "otro-secreto-igual-de-largo-pero-distinto-000".getBytes()), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.verificar(ajeno)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("un token de otro emisor o de otra audiencia tampoco")
    void emisorYAudiencia() {
        String otroEmisor = Jwts.builder()
                .subject("ana@ejemplo.com")
                .issuer("otra-tienda")
                .audience().add("smartzone-api").and()
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes()), Jwts.SIG.HS256)
                .compact();

        String otraAudiencia = Jwts.builder()
                .subject("ana@ejemplo.com")
                .issuer("smartzone-usuarios")
                .audience().add("otra-api").and()
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes()), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.verificar(otroEmisor)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.verificar(otraAudiencia)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("un token caducado no pasa")
    void caducado() {
        JwtProperties yaCaducado = new JwtProperties(
                SECRETO, "smartzone-usuarios", "smartzone-api",
                -1_000L, -1_000L, -1_000L, -1_000L);
        JwtService emisor = new JwtService(yaCaducado, roles);

        String token = emisor.generarAccessToken(usuario("CLIENTE"));

        assertThatThrownBy(() -> jwtService.verificar(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("un token SIN firma («alg: none») no pasa")
    void sinFirma() {
        /*
         * El ataque clásico contra JWT: se quita la firma y se pone `alg: none`
         * esperando que el verificador se crea la cabecera. `verifyWith` fija la
         * clave y el algoritmo, así que ni se mira lo que diga el token.
         */
        String sinFirmar = Jwts.builder()
                .subject("ana@ejemplo.com")
                .issuer("smartzone-usuarios")
                .audience().add("smartzone-api").and()
                .claim("rol", "ADMINISTRADOR")
                .compact();

        assertThatThrownBy(() -> jwtService.verificar(sinFirmar)).isInstanceOf(JwtException.class);
    }

    /* ══════════════ Arranque ══════════════ */

    @Test
    @DisplayName("un secreto corto detiene el arranque en vez de firmar con criptografía débil")
    void secretoCorto() {
        JwtProperties corto = new JwtProperties(
                "demasiado-corto", "e", "a", 1L, 1L, 1L, 1L);

        assertThatThrownBy(() -> new JwtService(corto, roles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("un secreto de ejemplo también lo detiene: es tan público como no tener ninguno")
    void secretoDeEjemplo() {
        JwtProperties ejemplo = new JwtProperties(
                "cambia-esto-por-un-secreto-de-verdad-largo", "e", "a", 1L, 1L, 1L, 1L);

        assertThatThrownBy(() -> new JwtService(ejemplo, roles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valor de ejemplo");
    }

    @Test
    @DisplayName("un secreto propio y largo arranca sin quejarse")
    void secretoValido() {
        assertThatCode(() -> new JwtService(propiedades, roles)).doesNotThrowAnyException();
    }

    /* ══════════════ Duración por rol ══════════════ */

    @Test
    @DisplayName("el administrador tiene la sesión MÁS corta, no la más larga")
    void duracionPorRol() {
        /*
         * Es al revés de lo que se esperaría por comodidad, y a propósito: el
         * token que más daño hace si se filtra es el que más permisos lleva.
         */
        assertThat(jwtService.duracionSegundos("ADMINISTRADOR"))
                .isLessThan(jwtService.duracionSegundos("CLIENTE"))
                .isLessThan(jwtService.duracionSegundos("EMPLEADO"));
    }

    @Test
    @DisplayName("un rol creado desde el panel dura lo mismo que un cliente")
    void rolDesconocido() {
        // Los roles son datos: no puede haber un switch exhaustivo. Lo que
        // importa es que un rol nuevo NO herede la duración del administrador.
        assertThat(jwtService.duracionSegundos("REPARTIDOR"))
                .isEqualTo(jwtService.duracionSegundos("CLIENTE"));
    }
}
