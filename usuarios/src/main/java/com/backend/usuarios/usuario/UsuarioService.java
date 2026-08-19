package com.backend.usuarios.usuario;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.shared.error.ConflictoException;
import com.backend.usuarios.shared.error.RecursoNoEncontradoException;
import com.backend.usuarios.usuario.dto.UsuarioDtos.CambioRol;
import com.backend.usuarios.usuario.dto.DireccionUsuario;
import com.backend.usuarios.usuario.dto.UsuarioDtos.PerfilUpdate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioCreate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository repositorio;
    private final RolService roles;
    private final PasswordEncoder codificador;
    private final AuditoriaService auditoria;
    private final MetricasSeguridad metricas;

    public List<UsuarioResponse> listar() {
        return repositorio.findAllByOrderByIdAsc().stream()
                .map(u -> UsuarioResponse.desde(u, roles.permisosDe(u.getRol())))
                .toList();
    }

    public UsuarioResponse obtener(Long id) {
        Usuario usuario = buscar(id);
        return UsuarioResponse.desde(usuario, roles.permisosDe(usuario.getRol()));
    }

    public Usuario buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no encontrado"));
    }

    public Usuario buscarPorEmail(String email) {
        return repositorio.findByEmailAddress(normalizar(email))
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    @Transactional
    public UsuarioResponse crear(UsuarioCreate dto) {
        String email = normalizar(dto.emailAddress());
        if (repositorio.existsByEmailAddress(email)) {
            throw new ConflictoException("Ya existe un usuario con el correo " + email);
        }

        String rol = dto.rol() == null || dto.rol().isBlank() ? "CLIENTE" : dto.rol().trim().toUpperCase();
        if (!roles.existe(rol)) {
            throw new RecursoNoEncontradoException("El rol " + rol + " no existe");
        }

        Usuario usuario = Usuario.builder()
                .name(dto.name())
                .lastname(dto.lastname())
                .emailAddress(email)
                .password(codificador.encode(dto.password()))
                .phoneNumber(dto.phoneNumber())
                .address(dto.address())
                .rol(rol)
                .build();

        Usuario guardado = repositorio.save(usuario);
        return UsuarioResponse.desde(guardado, roles.permisosDe(rol));
    }

    /** Actualiza solo los campos del perfil. Rol y contraseña quedan intactos. */
    @Transactional
    public UsuarioResponse actualizarPerfil(Long id, PerfilUpdate dto) {
        Usuario usuario = buscar(id);
        String email = normalizar(dto.emailAddress());

        if (!email.equals(usuario.getEmailAddress()) && repositorio.existsByEmailAddress(email)) {
            throw new ConflictoException("Ese correo ya está en uso");
        }

        usuario.setName(dto.name());
        usuario.setLastname(dto.lastname());
        usuario.setEmailAddress(email);
        usuario.setPhoneNumber(dto.phoneNumber());

        Usuario guardado = repositorio.save(usuario);
        return UsuarioResponse.desde(guardado, roles.permisosDe(guardado.getRol()));
    }

    /**
     * Guarda la direccion de entrega del perfil.
     *
     * <p>Va aparte del resto del perfil porque son cosas distintas: el nombre y
     * el correo se corrigen una vez, y la direccion se cambia al mudarse o al
     * mandar un pedido a otro sitio. Mezcladas en el mismo formulario, cambiar
     * de casa obligaba a repasar el correo.
     */
    @Transactional
    public UsuarioResponse guardarDireccion(Long id, DireccionUsuario direccion) {
        Usuario usuario = buscar(id);
        direccion.aplicarA(usuario);

        Usuario guardado = repositorio.save(usuario);
        return UsuarioResponse.desde(guardado, roles.permisosDe(guardado.getRol()));
    }

    @Transactional
    public UsuarioResponse cambiarRol(Long id, CambioRol dto) {
        Usuario usuario = buscar(id);
        String rolNuevo = dto.rol().trim().toUpperCase();
        if (!roles.existe(rolNuevo)) {
            throw new RecursoNoEncontradoException("El rol " + rolNuevo + " no existe");
        }

        String anterior = usuario.getRol();
        usuario.setRol(rolNuevo);
        Usuario guardado = repositorio.save(usuario);

        // A01: el rol viaja dentro del JWT, así que un token emitido antes del
        // cambio seguiría concediendo el rol viejo hasta caducar. Se registra
        // para que quede traza; la ventana la acota la vida corta del token.
        auditoria.registrar(Evento.CAMBIO_ROL, usuario.getEmailAddress(),
                "de=%s a=%s".formatted(anterior, rolNuevo));
        // Se cuenta al final: un intento sobre un usuario inexistente no es un
        // cambio de rol y no debe inflar la métrica.
        metricas.cambioRol(rolNuevo);

        return UsuarioResponse.desde(guardado, roles.permisosDe(rolNuevo));
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario usuario = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no encontrado"));

        String correo = usuario.getEmailAddress();
        repositorio.deleteById(id);
        auditoria.registrar(Evento.USUARIO_ELIMINADO, correo, "id=" + id);
    }

    static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
