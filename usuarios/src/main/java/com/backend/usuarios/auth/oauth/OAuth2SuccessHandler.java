package com.backend.usuarios.auth.oauth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.backend.usuarios.auth.JwtService;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.usuario.Proveedor;
import com.backend.usuarios.usuario.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cierra el flujo OAuth: crea o recupera la cuenta, emite el JWT y devuelve el
 * navegador al frontend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2Service oauth2Service;
    private final JwtService jwtService;
    private final AuditoriaService auditoria;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            redirigirConError(response, "Autenticación OAuth inesperada");
            return;
        }

        try {
            Proveedor proveedor = Proveedor.desdeRegistrationId(token.getAuthorizedClientRegistrationId());
            OAuth2User principal = token.getPrincipal();

            DatosOAuth datos = DatosOAuth.desde(proveedor, principal.getAttributes());
            Usuario usuario = oauth2Service.buscarOCrear(proveedor, datos);

            auditoria.registrar(Evento.LOGIN_OAUTH, usuario.getEmailAddress(),
                    "proveedor=" + proveedor);

            String accessToken = jwtService.generarAccessToken(usuario);
            String refreshToken = jwtService.generarRefreshToken(usuario);

            // El token viaja en el fragmento (#), no en la query: así no queda
            // en los logs del servidor ni en la cabecera Referer.
            String destino = "%s/oauth/callback#token=%s&refresh=%s".formatted(
                    frontendUrl,
                    codificar(accessToken),
                    codificar(refreshToken));

            response.sendRedirect(destino);

        } catch (OAuth2Exception ex) {
            log.warn("OAuth rechazado: {}", ex.getMessage());
            redirigirConError(response, ex.getMessage());

        } catch (RuntimeException ex) {
            log.error("Fallo inesperado en el flujo OAuth", ex);
            redirigirConError(response, "No se pudo completar el inicio de sesión");
        }
    }

    private void redirigirConError(HttpServletResponse response, String mensaje) throws IOException {
        response.sendRedirect("%s/login?error=%s".formatted(frontendUrl, codificar(mensaje)));
    }

    private String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
