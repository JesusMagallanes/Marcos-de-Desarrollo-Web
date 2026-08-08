package com.backend.usuarios.auth.oauth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/** Si el usuario cancela en Google/Facebook, vuelve al login con un aviso. */
@Component
@Slf4j
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        log.warn("Fallo de autenticación OAuth: {}", exception.getMessage());

        String mensaje = URLEncoder.encode(
                "No se pudo iniciar sesión con el proveedor", StandardCharsets.UTF_8);
        response.sendRedirect("%s/login?error=%s".formatted(frontendUrl, mensaje));
    }
}
