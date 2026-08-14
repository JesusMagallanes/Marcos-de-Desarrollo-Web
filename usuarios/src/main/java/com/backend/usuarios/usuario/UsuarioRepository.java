package com.backend.usuarios.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailAddress(String emailAddress);

    boolean existsByEmailAddress(String emailAddress);

    List<Usuario> findAllByOrderByIdAsc();

    long countByRol(String rol);

    /** {@code [rol, total]} para pintar cuántos usuarios tiene cada rol. */
    @Query("select u.rol, count(u) from Usuario u group by u.rol")
    List<Object[]> contarPorRol();
}
