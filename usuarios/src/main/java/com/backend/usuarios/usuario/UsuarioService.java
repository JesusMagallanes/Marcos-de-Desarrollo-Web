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
import com.backend.usuarios.usuario.dto.UsuarioDtos.PerfilUpdate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioCreate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder codificador;
    private final AuditoriaService auditoria;
    private final MetricasSeguridad metricas;

    public List<UsuarioResponse> listar() {
        return repositorio.findAllByOrderByIdAsc().stream().map(UsuarioResponse::desde).toList();
    }

    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.desde(buscar(id));
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

        Usuario usuario = Usuario.builder()
                .name(dto.name())
                .lastname(dto.lastname())
                .emailAddress(email)
                .password(codificador.encode(dto.password()))
                .phoneNumber(dto.phoneNumber())
                .address(dto.address())
                .rol(dto.rol() != null ? dto.rol() : Rol.CLIENTE)
                .build();

        return UsuarioResponse.desde(repositorio.save(usuario));
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
        usuario.setAddress(dto.address());

        return UsuarioResponse.desde(repositorio.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarRol(Long id, CambioRol dto) {
        Usuario usuario = buscar(id);
        Rol anterior = usuario.getRol();
        usuario.setRol(dto.rol());
        Usuario guardado = repositorio.save(usuario);

        // A01: el rol viaja dentro del JWT, así que un token emitido antes del
        // cambio seguiría concediendo el rol viejo hasta caducar. Se registra
        // para que quede traza; la ventana la acota la vida corta del token.
        auditoria.registrar(Evento.CAMBIO_ROL, usuario.getEmailAddress(),
                "de=%s a=%s".formatted(anterior, dto.rol()));
        // Se cuenta al final: un intento sobre un usuario inexistente no es un
        // cambio de rol y no debe inflar la métrica.
        metricas.cambioRol(dto.rol().name());

        return UsuarioResponse.desde(guardado);
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
