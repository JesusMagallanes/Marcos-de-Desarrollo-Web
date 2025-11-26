package Pry_01.Web.de.Ventas.de.Computadoras.Configuration.User;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class UsuarioDetails implements UserDetails {
    private final UsuarioModel usuarioModel;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = usuarioModel.getRol() != null ? usuarioModel.getRol().name() : "CLIENTE";
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        return usuarioModel.getPassword();
    }

    @Override
    public String getUsername() {
        return usuarioModel.getEmailAddress();
    }
    
    public String getNombreCompletos() {
        return usuarioModel.getName() + " " + usuarioModel.getLastname();
    }

    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
