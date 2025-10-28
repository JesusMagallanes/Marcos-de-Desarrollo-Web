package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioModel> listarUsuario() {
        return usuarioRepository.findAll();
    }

    public UsuarioModel guardarUsuario(UsuarioModel usuario) {
        return usuarioRepository.save(usuario);
    }

    public UsuarioModel obtenerPorId(Long id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    public void eliminarUsuario(Long id) {
        usuarioRepository.deleteById(id);
    }

    public void actualizarUsuario(UsuarioModel usuario) {
        usuarioRepository.save(usuario);
    }

    public UsuarioModel registrarUsuario(UsuarioModel usuario) {
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public UsuarioModel login(String email, String password) {
        return usuarioRepository.findByEmailAddress(email)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()))
                .orElse(null);
    }
}
