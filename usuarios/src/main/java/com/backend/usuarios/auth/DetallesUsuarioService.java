package com.backend.usuarios.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** Solo se usa durante el login; después todo va por JWT. */
@Service
@RequiredArgsConstructor
public class DetallesUsuarioService implements UserDetailsService {

    private final UsuarioRepository repositorio;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = repositorio.findByEmailAddress(email == null ? null : email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        // Las cuentas de Google/Facebook no tienen contraseña utilizable. Se
        // rechazan aquí con el mismo mensaje genérico, sin revelar el motivo.
        if (!usuario.puedeIniciarSesionConPassword()) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }

        return User.withUsername(usuario.getEmailAddress())
                .password(usuario.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))
                .build();
    }
}
