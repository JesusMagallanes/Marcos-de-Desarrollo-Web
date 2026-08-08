package com.backend.usuarios.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailAddress(String emailAddress);

    boolean existsByEmailAddress(String emailAddress);

    List<Usuario> findAllByOrderByIdAsc();
}
