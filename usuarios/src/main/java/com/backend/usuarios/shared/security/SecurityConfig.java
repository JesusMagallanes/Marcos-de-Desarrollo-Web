package com.backend.usuarios.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.backend.usuarios.auth.oauth.OAuth2FailureHandler;
import com.backend.usuarios.auth.oauth.OAuth2SuccessHandler;
import com.backend.usuarios.shared.seguridad.CabecerasSeguridad;

import lombok.RequiredArgsConstructor;

/** Dos cadenas, porque hay dos modelos de sesión conviviendo: */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProperties propiedades;

    /**
     * A02: factor de coste 12 en lugar del 10 por defecto. Encarece el ataque por
     * diccionario si alguna vez se filtra la tabla de usuarios.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService detalles, PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(detalles);
        proveedor.setPasswordEncoder(codificador);
        return new ProviderManager(proveedor);
    }

    /** Misma clave HMAC que usa el emisor; los otros 3 servicios replican este bean. */
    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec clave = new SecretKeySpec(
                propiedades.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Convierte el claim `rol` en la autoridad ROLE_x que espera @PreAuthorize. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthorityPrefix("ROLE_");
        autoridades.setAuthoritiesClaimName("rol");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }

    /** Cadena 1: solo el handshake con Google y Facebook. */
    @Bean
    @Order(1)
    SecurityFilterChain cadenaOAuth(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registros,
            OAuth2SuccessHandler successHandler,
            OAuth2FailureHandler failureHandler) throws Exception {

        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // IF_REQUIRED, no STATELESS: sin sesión se pierde el parámetro
                // `state` y el callback falla con "authorization_request_not_found".
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        // Sin oauth.properties no hay proveedores registrados. En vez de tumbar
        // el arranque, se cierran esas rutas y el servicio funciona sin login social.
        if (registros.getIfAvailable() != null) {
            http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .oauth2Login(oauth -> oauth
                            .successHandler(successHandler)
                            .failureHandler(failureHandler));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        }

        return http.build();
    }

    /** Cadena 2: la API, totalmente stateless. */
    @Bean
    @Order(2)
    SecurityFilterChain cadenaApi(HttpSecurity http, JwtAuthenticationConverter convertidor,
            RespuestasSeguridad respuestas) throws Exception {
        http
                // CSRF deshabilitado es correcto AQUÍ porque la autenticación
                // va por cabecera Authorization, no por cookie: el navegador no
                // adjunta el token de forma automática, así que no hay vector CSRF.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(CabecerasSeguridad.paraApi())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registrar",
                                "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/proveedores").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Las métricas revelan intentos de login y sesiones: solo ADMINISTRADOR.
                        .requestMatchers("/actuator/prometheus").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                // 401 y 403 con el mismo formato RFC 7807 que el resto de errores.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(respuestas)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)));

        return http.build();
    }

    /**
     * En producción el gateway es el único origen. En desarrollo Angular corre en :4200
     * con proxy, así que esto cubre el acceso directo al servicio.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:8080"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
