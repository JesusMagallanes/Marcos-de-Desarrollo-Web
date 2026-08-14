package com.backend.usuarios.usuario;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.shared.error.ConflictoException;
import com.backend.usuarios.shared.error.RecursoNoEncontradoException;
import com.backend.usuarios.usuario.dto.RolDtos.PermisoInfo;
import com.backend.usuarios.usuario.dto.RolDtos.RolCreate;
import com.backend.usuarios.usuario.dto.RolDtos.RolResponse;
import com.backend.usuarios.usuario.dto.RolDtos.RolUpdate;

import lombok.RequiredArgsConstructor;

/** Gestión de roles y permisos — recurso `/api/roles`. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolService {

    /** Permisos que el rol ADMINISTRADOR nunca puede perder: se bloquearía. */
    private static final Set<Permiso> PERMISOS_BLOQUEADOS_ADMIN = Set.of(
            Permiso.ROLES_GESTIONAR, Permiso.USUARIOS_GESTIONAR);

    private final RolRepository repositorio;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public List<RolResponse> listar() {
        Map<String, Long> conteos = new HashMap<>();
        for (Object[] fila : usuarios.contarPorRol()) {
            conteos.put((String) fila[0], (Long) fila[1]);
        }
        return repositorio.findAllByOrderByTipoAscNombreAsc().stream()
                .map(rol -> RolResponse.desde(rol, conteos.getOrDefault(rol.getNombre(), 0L)))
                .toList();
    }

    /** El catálogo fijo de permisos, agrupado por módulo para el panel. */
    public List<PermisoInfo> catalogoPermisos() {
        return java.util.Arrays.stream(Permiso.values())
                .sorted(Comparator.comparing(Permiso::modulo).thenComparing(Permiso::name))
                .map(PermisoInfo::desde)
                .toList();
    }

    /** Los códigos de permiso de un rol, para el claim `permisos` del JWT. */
    public List<String> permisosDe(String rol) {
        if (rol == null) {
            return List.of();
        }
        return repositorio.findById(rol)
                .map(r -> r.getPermisos().stream().map(Enum::name).sorted().toList())
                .orElseGet(List::of);
    }

    public boolean existe(String nombre) {
        return nombre != null && repositorio.existsById(nombre);
    }

    public RolResponse obtener(String nombre) {
        return RolResponse.desde(buscar(nombre), usuarios.countByRol(nombre));
    }

    @Transactional
    public RolResponse crear(RolCreate dto) {
        String nombre = dto.nombre();
        if (repositorio.existsById(nombre)) {
            throw new ConflictoException("Ya existe un rol llamado " + nombre);
        }

        Rol rol = Rol.builder()
                .nombre(nombre)
                .descripcion(dto.descripcion() == null ? "" : dto.descripcion())
                .tipo(dto.tipo())
                .sistema(false)
                .permisos(dto.permisos())
                .build();

        Rol guardado = repositorio.save(rol);
        auditoria.registrar(Evento.ROL_CREADO, "rol=" + nombre,
                "tipo=%s permisos=%s".formatted(dto.tipo(), resumir(dto.permisos())));
        return RolResponse.desde(guardado, 0);
    }

    @Transactional
    public RolResponse actualizar(String nombre, RolUpdate dto) {
        Rol rol = buscar(nombre);

        // Los roles de sistema no cambian de tipo: CLIENTE siempre es cliente,
        // EMPLEADO y ADMINISTRADOR siempre son trabajadores.
        if (rol.isSistema() && dto.tipo() != null && dto.tipo() != rol.getTipo()) {
            throw new ConflictoException("El tipo del rol de sistema " + nombre + " no se puede cambiar");
        }

        // Red de seguridad: el ADMINISTRADOR no puede quedarse sin gestionar
        // roles ni usuarios, o el panel se encerraría a sí mismo.
        if ("ADMINISTRADOR".equals(nombre)) {
            Set<Permiso> nuevos = dto.permisos();
            Set<Permiso> faltan = PERMISOS_BLOQUEADOS_ADMIN.stream()
                    .filter(p -> !nuevos.contains(p))
                    .collect(Collectors.toSet());
            if (!faltan.isEmpty()) {
                throw new ConflictoException(
                        "El rol ADMINISTRADOR no puede perder los permisos "
                                + faltan.stream().map(Enum::name).sorted().toList());
            }
        }

        Set<Permiso> anteriores = rol.getPermisos();
        rol.setDescripcion(dto.descripcion() == null ? "" : dto.descripcion());
        rol.setTipo(dto.tipo() == null ? rol.getTipo() : dto.tipo());
        rol.setPermisos(dto.permisos());

        Rol guardado = repositorio.save(rol);
        auditoria.registrar(Evento.ROL_EDITADO, "rol=" + nombre,
                "de=%s a=%s".formatted(resumir(anteriores), resumir(dto.permisos())));
        return RolResponse.desde(guardado, usuarios.countByRol(nombre));
    }

    @Transactional
    public void eliminar(String nombre) {
        Rol rol = buscar(nombre);
        if (rol.isSistema()) {
            throw new ConflictoException("Los roles de sistema no se pueden eliminar");
        }
        long conUsuarios = usuarios.countByRol(nombre);
        if (conUsuarios > 0) {
            throw new ConflictoException(
                    "Hay " + conUsuarios + " usuario(s) con el rol " + nombre + ": asígnale otro primero");
        }

        repositorio.delete(rol);
        auditoria.registrar(Evento.ROL_ELIMINADO, "rol=" + nombre, "tipo=" + rol.getTipo());
    }

    private Rol buscar(String nombre) {
        return repositorio.findById(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol " + nombre + " no encontrado"));
    }

    private static String resumir(Set<Permiso> permisos) {
        return permisos.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
