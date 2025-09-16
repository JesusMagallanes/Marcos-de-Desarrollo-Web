package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;

@Service
public class UsuarioService {
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioModel> listarUsuarios() {
        return usuarioRepository.findAll();
    }

    public UsuarioModel guardarUsuario(UsuarioModel usuario) {
        if (usuarioRepository.existsByEmailAddress(usuario.getEmailAddress())) {
            throw new IllegalArgumentException("El coreo ya está en uso");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        try {
            return usuarioRepository.save(usuario);
        } catch (Exception e) {
            e.printStackTrace(); // imprime la causa real en consola
            throw e;
        }
    }
}
