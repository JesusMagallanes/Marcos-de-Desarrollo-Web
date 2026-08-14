package com.backend.usuarios.usuario;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol configurable desde el panel de administración. El nombre es la clave
 * natural y también el valor del claim `rol` del JWT, de modo que el resto de
 * servicios siguen leyéndolo igual que con el antiguo enum.
 *
 * Los roles de sistema (CLIENTE, EMPLEADO, ADMINISTRADOR) no se pueden borrar
 * ni cambiar de tipo; sí se les pueden asignar permisos.
 */
@Entity
@Table(name = "rol")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rol {

    /** Clave natural: es el claim `rol` del JWT, en mayúsculas. */
    @Id
    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(length = 200)
    @Builder.Default
    private String descripcion = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoRol tipo;

    /** Los roles de sistema no se eliminan ni se cambian de tipo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean sistema = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rol_permiso", joinColumns = @JoinColumn(name = "rol_nombre"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permiso", length = 40)
    @Builder.Default
    private Set<Permiso> permisos = new HashSet<>();

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (descripcion == null) {
            descripcion = "";
        }
        if (permisos == null) {
            permisos = new HashSet<>();
        }
    }

    public void setNombre(String nombre) {
        this.nombre = nombre == null ? null : nombre.trim().toUpperCase();
    }
}
