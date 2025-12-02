package Pry_01.Web.de.Ventas.de.Computadoras.Configuration;

import java.io.IOException;

import org.springframework.security.core.Authentication;
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
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler  {

    private final UsuarioService usuarioService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        DefaultOAuth2User oauthUser = (DefaultOAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String nombre = oauthUser.getAttribute("name");

        if (email == null) {
            throw new RuntimeException("No se pudo obtener el email de Google.");
        }

        UsuarioModel usuario = usuarioService.obtenerPorEmail(email);

        if (usuario == null) {

            usuario = new UsuarioModel();
            usuario.setEmailAddress(email);

            if (nombre != null && nombre.contains(" ")) {
                String[] partes = nombre.split(" ", 2);
                usuario.setName(partes[0]);
                usuario.setLastname(partes[1]);
            } else {
                usuario.setName(nombre != null ? nombre : "Usuario");
                usuario.setLastname("Google");
            }

            usuario.setPassword("Aa@12345");  

            usuario.setPhoneNumber("999999999");

            usuario.setAddress("Google OAuth");

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
