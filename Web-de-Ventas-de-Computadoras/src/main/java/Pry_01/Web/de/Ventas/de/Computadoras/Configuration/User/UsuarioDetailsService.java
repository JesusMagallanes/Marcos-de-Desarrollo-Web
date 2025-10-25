package Pry_01.Web.de.Ventas.de.Computadoras.Configuration.User;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService{
    
    private final UsuarioService usuarioService;
    
    @Override
    public UserDetails loadUserByUsername(String emailUsuario) throws UsernameNotFoundException {
        UsuarioModel usuarioModel = usuarioService.getCorreo(emailUsuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el correo: " + emailUsuario));
        return new UsuarioDetails(usuarioModel);
    }
}