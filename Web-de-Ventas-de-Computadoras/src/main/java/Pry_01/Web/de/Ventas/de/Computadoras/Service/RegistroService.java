package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth.AuthRegisterRequest;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth.AuthRegisterResponse;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthRegisterResponse registrarCliente(AuthRegisterRequest request) {
        log.info("Registrando nuevo usuario: {}", request.getEmailAddress());

        if (usuarioRepository.existsByEmailAddress(request.getEmailAddress())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo");
        }

        UsuarioModel nuevoUsuario = UsuarioModel.builder()
                .name(request.getNombre())
                .lastname(request.getApellido())
                .emailAddress(request.getEmailAddress())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getTelefono())
                .address(request.getDireccion())
                .rol(Roles.CLIENTE) // o el rol que definas por defecto
                .build();

        usuarioRepository.save(nuevoUsuario);

        return new AuthRegisterResponse(nuevoUsuario.getId(),nuevoUsuario.getName(),nuevoUsuario.getEmailAddress());
    }

}
