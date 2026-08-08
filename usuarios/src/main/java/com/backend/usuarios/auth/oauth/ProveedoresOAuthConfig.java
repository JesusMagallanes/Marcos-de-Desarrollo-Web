package com.backend.usuarios.auth.oauth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import lombok.extern.slf4j.Slf4j;

/** Registra Google y Facebook leyendo las credenciales del `.env`. */
@Configuration
@Conditional(ProveedoresOAuthConfig.HayAlgunProveedor.class)
@Slf4j
public class ProveedoresOAuthConfig {

    @Value("${seguridad.oauth.google.client-id:}")
    private String googleId;

    @Value("${seguridad.oauth.google.client-secret:}")
    private String googleSecret;

    @Value("${seguridad.oauth.facebook.client-id:}")
    private String facebookId;

    @Value("${seguridad.oauth.facebook.client-secret:}")
    private String facebookSecret;

    @Value("${app.oauth-redirect-base}")
    private String baseRedireccion;

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registros = new ArrayList<>();

        if (configurado(googleId, googleSecret)) {
            registros.add(google());
            log.info("Inicio de sesión con Google habilitado");
        }
        if (configurado(facebookId, facebookSecret)) {
            registros.add(facebook());
            log.info("Inicio de sesión con Facebook habilitado");
        }

        return new InMemoryClientRegistrationRepository(registros);
    }

    private ClientRegistration google() {
        return ClientRegistration.withRegistrationId("google")
                .clientId(googleId.trim())
                .clientSecret(googleSecret.trim())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(baseRedireccion + "/login/oauth2/code/google")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .clientName("Google")
                .build();
    }

    private ClientRegistration facebook() {
        return ClientRegistration.withRegistrationId("facebook")
                .clientId(facebookId.trim())
                .clientSecret(facebookSecret.trim())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(baseRedireccion + "/login/oauth2/code/facebook")
                .scope("email", "public_profile")
                .authorizationUri("https://www.facebook.com/v21.0/dialog/oauth")
                .tokenUri("https://graph.facebook.com/v21.0/oauth/access_token")
                // Facebook no devuelve el correo salvo que se pida en `fields`.
                .userInfoUri("https://graph.facebook.com/me?fields=id,name,email")
                .userNameAttributeName("id")
                .clientName("Facebook")
                .build();
    }

    private static boolean configurado(String id, String secreto) {
        return id != null && !id.isBlank() && secreto != null && !secreto.isBlank();
    }

    /**
     * Solo se crea el repositorio si hay al menos un proveedor con credenciales; si no, ni
     * siquiera existe el bean y la cadena OAuth de SecurityConfig cierra esas rutas.
     */
    static class HayAlgunProveedor implements Condition {

        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return tiene(contexto, "seguridad.oauth.google.client-id")
                    || tiene(contexto, "seguridad.oauth.facebook.client-id");
        }

        private boolean tiene(ConditionContext contexto, String propiedad) {
            String valor = contexto.getEnvironment().getProperty(propiedad);
            return valor != null && !valor.isBlank();
        }
    }
}
