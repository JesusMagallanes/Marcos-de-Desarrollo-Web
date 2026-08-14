package com.backend.usuarios.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, String> {

    /** Trabajadores primero, luego clientes; dentro del tipo, por nombre. */
    List<Rol> findAllByOrderByTipoAscNombreAsc();

    Optional<Rol> findByNombre(String nombre);
}
