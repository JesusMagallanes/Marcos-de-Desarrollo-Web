package com.backend.usuarios.usuario.dto;

import java.util.Set;

import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Permiso;
import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.TipoRol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs del recurso rol: el rol viaja por nombre (clave natural). */
public final class RolDtos {

    private RolDtos() {
    }

    public static final int MAX_NOMBRE = 50;
    public static final int MAX_DESCRIPCION = 200;

    /** Letras, números y guión bajo; el nombre es el claim `rol` del JWT. */
    public static final String PATRON_NOMBRE = "^[A-Z0-9_]{2," + MAX_NOMBRE + "}$";
    public static final String MENSAJE_NOMBRE = "El nombre debe tener 2-50 caracteres en mayúsculas (A-Z, 0-9, _)";

    /** Un rol tal como se muestra en el panel. */
    public record RolResponse(
            String nombre,
            String descripcion,
            TipoRol tipo,
            boolean sistema,
            Set<Permiso> permisos,
            long usuarios) {

        public static RolResponse desde(Rol rol, long usuarios) {
            return new RolResponse(
                    rol.getNombre(),
                    rol.getDescripcion(),
                    rol.getTipo(),
                    rol.isSistema(),
                    rol.getPermisos(),
                    usuarios);
        }
    }

    /** Un permiso del catálogo, para pintar el selector en el panel. */
    public record PermisoInfo(String codigo, String descripcion, String modulo) {

        public static PermisoInfo desde(Permiso permiso) {
            return new PermisoInfo(permiso.name(), permiso.descripcion(), permiso.modulo());
        }
    }

    /** Alta de un rol nuevo. */
    public record RolCreate(
            @NotBlank @Pattern(regexp = PATRON_NOMBRE, message = MENSAJE_NOMBRE) String nombre,
            @Size(max = MAX_DESCRIPCION) String descripcion,
            @NotNull TipoRol tipo,
            Set<Permiso> permisos) {

        public RolCreate {
            nombre = Saneador.texto(nombre);
            descripcion = Saneador.texto(descripcion);
            if (permisos == null || permisos.isEmpty()) {
                throw new IllegalArgumentException("Un rol necesita al menos un permiso");
            }
        }
    }

    /** Edición de un rol: permisos y descripción; el nombre es la ruta. */
    public record RolUpdate(
            @Size(max = MAX_DESCRIPCION) String descripcion,
            TipoRol tipo,
            Set<Permiso> permisos) {

        public RolUpdate {
            descripcion = Saneador.texto(descripcion);
            if (permisos == null || permisos.isEmpty()) {
                throw new IllegalArgumentException("Un rol necesita al menos un permiso");
            }
        }
    }
}
