package com.backend.usuarios.auth.oauth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.usuario.Proveedor;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2Service {

    private final UsuarioRepository repositorio;

    /** Busca la cuenta por correo o la crea. */
    @Transactional
    public Usuario buscarOCrear(Proveedor proveedor, DatosOAuth datos) {
        if (datos.email() == null || datos.email().isBlank()) {
            throw new OAuth2Exception("El proveedor no entregó un correo electrónico");
        }

        // Sin correo verificado no se vincula nada: si no, bastaría con crear
        // una cuenta en el proveedor usando el correo de otra persona para
        // apropiarse de su cuenta local.
        if (!datos.emailVerificado()) {
            throw new OAuth2Exception("El correo de tu cuenta no está verificado");
        }

        String email = datos.email().trim().toLowerCase();

        return repositorio.findByEmailAddress(email)
                .map(existente -> vincular(existente, proveedor))
                .orElseGet(() -> crear(proveedor, datos, email));
    }

    /**
     * La cuenta ya existía. Si era local, se respeta: el usuario conserva su contraseña y
     * simplemente ha entrado por otra puerta con un correo que el proveedor confirmó que
     * le pertenece.
     */
    private Usuario vincular(Usuario usuario, Proveedor proveedor) {
        log.info("Inicio de sesión con {} para la cuenta existente {}", proveedor, usuario.getEmailAddress());
        return usuario;
    }

    private Usuario crear(Proveedor proveedor, DatosOAuth datos, String email) {
        Usuario usuario = Usuario.builder()
                .name(datos.nombre() != null ? datos.nombre() : "Usuario")
                .lastname(datos.apellido() != null && !datos.apellido().isBlank()
                        ? datos.apellido()
                        : proveedor.name())
                .emailAddress(email)
                .password(null)
                .proveedor(proveedor)
                .rol("CLIENTE")
                // Teléfono y dirección los completa el usuario en "Mi cuenta":
                // el proveedor no los entrega y no tiene sentido inventarlos.
                .phoneNumber(null)
                .address(null)
                .build();

        Usuario guardado = repositorio.save(usuario);
        log.info("Cuenta creada vía {} para {}", proveedor, email);
        return guardado;
    }
}
