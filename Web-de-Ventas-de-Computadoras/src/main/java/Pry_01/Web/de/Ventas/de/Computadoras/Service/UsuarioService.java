package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;

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
    public Optional<UsuarioModel> getCorreo(String correo) {
        return usuarioRepository.findByEmailAddress(correo);
    }

    public List<UsuarioModel> listarUsuario() {
        return usuarioRepository.findAll();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public UsuarioModel guardarUsuario(UsuarioModel usuario) {
        if (usuario.getEmailAddress() != null) {
            usuario.setEmailAddress(normalizeEmail(usuario.getEmailAddress()));
        }

        if (usuario.getPassword() != null &&
            !usuario.getPassword().startsWith("$2a$") &&
            !usuario.getPassword().startsWith("$2b$") &&
            !usuario.getPassword().startsWith("$2y$")) {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }

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
        if (usuario.getEmailAddress() != null) {
            usuario.setEmailAddress(normalizeEmail(usuario.getEmailAddress()));
        }

        String raw = usuario.getPassword();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Password vacía");
        }

        // Protección defensiva: si ya parece ser un hash de BCrypt, no lo codificamos de nuevo.
        if (raw.startsWith("$2a$") || raw.startsWith("$2b$") || raw.startsWith("$2y$")) {
            usuario.setPassword(raw);
        } else {
            usuario.setPassword(passwordEncoder.encode(raw));
        }

        return usuarioRepository.save(usuario);
    }

    public UsuarioModel login(String email, String password) {
        if (email == null || password == null) return null;
        String normalized = normalizeEmail(email);

        Optional<UsuarioModel> opt = usuarioRepository.findByEmailAddress(normalized);
        if (opt.isEmpty()) {
            return null;
        }

        UsuarioModel u = opt.get();
        String stored = u.getPassword();
        boolean matches = stored != null && passwordEncoder.matches(password, stored);
        return matches ? u : null;
    }
}
