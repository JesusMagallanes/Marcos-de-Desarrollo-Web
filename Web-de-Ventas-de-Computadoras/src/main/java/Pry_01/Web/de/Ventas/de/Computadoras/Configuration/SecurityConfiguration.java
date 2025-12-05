package Pry_01.Web.de.Ventas.de.Computadoras.Configuration;

import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.User.UsuarioDetails;
import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.User.UsuarioDetailsService;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(UsuarioService usuarioService) {
        return new OAuth2LoginSuccessHandler(usuarioService);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UsuarioDetailsService usuarioDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider prov = new DaoAuthenticationProvider();
        prov.setUserDetailsService(usuarioDetailsService);
        prov.setPasswordEncoder(passwordEncoder);
        return prov;
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(HttpServletRequest request,
                    HttpServletResponse response,
                    org.springframework.security.core.Authentication authentication)
                    throws IOException, ServletException {
                // establecer DTO en sesión (como ya hacías)
                Object principal = authentication.getPrincipal();
                if (principal instanceof UsuarioDetails) {
                    UsuarioDetails ud = (UsuarioDetails) principal;
                    UsuarioModel usuarioModel = ud.getUsuarioModel();
                    UsuarioDTO dto = new UsuarioDTO(usuarioModel);
                    request.getSession(true).setAttribute("usuario", dto);
                }

                // ver autoridades y redirigir según rol
                boolean isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

                if (isAdmin) {
                    response.sendRedirect(request.getContextPath() + "/VistaAdmin");
                } else {
                    response.sendRedirect(request.getContextPath() + "/Index");
                }
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            UsuarioDetailsService usuarioDetailsService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/chatbot/**"))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/VistaAdmin/**", "/fragments/Admin-gest/**", "/productos/admin/**")
                        .hasRole("ADMINISTRADOR")

                        .requestMatchers("/", "/Index", "/index", "/Css/**", "/Js/**", "/Img/**", "/fragment",
                                "/usuarios/registrar", "/Detalles", "/Somos", "/header", "/canales",
                                "/Canales", "/metodosPago", "/productosCategoria", "/usuarios/registrar/**",
                                "/productos", "/productos/api", "/productos/categoria/**", "/productos/**",
                                "/api/chatbot/**",
                                "/oauth2/authorization/google", "/login/oauth2/code/google", "/oauth2/authorization/facebook",
                                "/login/oauth2/code/facebook")
                        .permitAll()

                        .requestMatchers("/EnviosPag").hasAnyRole("EMPLEADO", "ADMINISTRADOR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/Index")
                        .loginProcessingUrl("/usuarios/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .failureUrl("/Index?error=true")
                        .permitAll())
                .oauth2Login(oauth -> oauth
                        .loginPage("/Index")
                        .successHandler(oAuth2LoginSuccessHandler))
                .rememberMe(rem -> rem
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(7 * 24 * 60 * 60)
                        .key("uniqueAndSecret")
                        .userDetailsService(usuarioDetailsService))
                .logout(logout -> logout
                        .logoutUrl("/usuarios/logout")
                        .logoutSuccessUrl("/Index?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me"));

        return http.build();
    }
}
