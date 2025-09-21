package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDto;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class UsuarioService {
    @Autowired
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

    public void eliminarUsuarioPorId(Long id) {
        if (usuarioRepository.existsById(id)) {
            usuarioRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Usuario con ID " + id + "no existe");
        }
    }

  public Optional<UsuarioModel> actualizarUsuario(Long id, UsuarioDto usuarioDto) {
    Optional<UsuarioModel> usuarioOptional = usuarioRepository.findById(id);
    if (!usuarioOptional.isPresent()) {
        return Optional.empty();
    }

    UsuarioModel usuario = usuarioOptional.get();

    if (usuarioDto.getName() != null) {
        usuario.setName(usuarioDto.getName());
    }
    if (usuarioDto.getLastname() != null) {
        usuario.setLastname(usuarioDto.getLastname());
    }
    if (usuarioDto.getEmailAddress() != null) {
        usuario.setEmailAddress(usuarioDto.getEmailAddress());
    }
    if (usuarioDto.getPhoneNumber() != null) {
        usuario.setPhoneNumber(usuarioDto.getPhoneNumber());
    }
    if (usuarioDto.getAddress() != null) {
        usuario.setAddress(usuarioDto.getAddress());
    }
    if (usuarioDto.getRol() != null) {
        usuario.setRol(usuarioDto.getRol());
    }

    UsuarioModel usuarioActualizado = usuarioRepository.save(usuario);

    return Optional.of(usuarioActualizado);
}

}