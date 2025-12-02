package Pry_01.Web.de.Ventas.de.Computadoras.Configuration;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UsuarioService usuarioService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        String provider = "unknown";
        if (authentication instanceof OAuth2AuthenticationToken) {
            provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

            DefaultOAuth2User oauthUser = (DefaultOAuth2User) authentication.getPrincipal();
            Map<String, Object> attrs = oauthUser.getAttributes();

            String email = (String) attrs.get("email");
            String name = (String) attrs.get("name");

            String pictureUrl = null;
            Object picObj = attrs.get("picture");
            if (picObj instanceof String) {
                pictureUrl = (String) picObj;
            } else if (picObj instanceof Map) {
                Map<?, ?> picMap = (Map<?, ?>) picObj;
                Object data = picMap.get("data");
                if (data instanceof Map) {
                    pictureUrl = (String) ((Map<?, ?>) data).get("url");
                }
            }

            if (email == null) {
                throw new RuntimeException("No se obtuvo email del proveedor OAuth2.");
            }

            UsuarioModel usuario = usuarioService.obtenerPorEmail(email);

            if (usuario == null) {
                usuario = new UsuarioModel();
                usuario.setEmailAddress(email);

                if (name != null && name.contains(" ")) {
                    String[] partes = name.split(" ", 2);
                    usuario.setName(partes[0]);
                    usuario.setLastname(partes.length > 1 ? partes[1] : "OAuth");
                } else {
                    usuario.setName(name != null ? name : "Usuario");
                    usuario.setLastname("OAuth");
                }

                usuario.setPassword("Aa@12345");
                usuario.setPhoneNumber("999999999");
                usuario.setAddress((provider.equals("facebook") ? "Facebook OAuth" : "Google OAuth"));
                usuario.setRol(Roles.CLIENTE);

                usuario = usuarioService.guardarUsuario(usuario);
            }

            UsuarioDTO dto = new UsuarioDTO(usuario);
            request.getSession(true).setAttribute("usuario", dto);

            boolean isAdmin = usuario.getRol() == Roles.ADMINISTRADOR;
            if (isAdmin) {
                response.sendRedirect("/VistaAdmin");
            } else {
                response.sendRedirect("/Index");
            }
        }
    }
}