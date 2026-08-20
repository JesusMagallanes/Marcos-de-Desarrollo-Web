package com.backend.usuarios.ubigeo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Las tres consultas que alimentan los desplegables en cascada.
 *
 * <p>Devuelven nombres y no entidades porque es lo único que necesita el
 * formulario: traer 1874 filas completas para pintar una lista de 25 opciones
 * sería trabajo para nada.
 */
public interface UbigeoRepository extends JpaRepository<Ubigeo, String> {

    @Query("SELECT DISTINCT u.departamento FROM Ubigeo u ORDER BY u.departamento")
    List<String> departamentos();

    @Query("""
            SELECT DISTINCT u.provincia FROM Ubigeo u
            WHERE u.departamento = :departamento
            ORDER BY u.provincia
            """)
    List<String> provinciasDe(@Param("departamento") String departamento);

    @Query("""
            SELECT DISTINCT u.distrito FROM Ubigeo u
            WHERE u.departamento = :departamento
              AND u.provincia = :provincia
            ORDER BY u.distrito
            """)
    List<String> distritosDe(@Param("departamento") String departamento,
            @Param("provincia") String provincia);

    /**
     * El ubigeo de un distrito concreto.
     *
     * <p>Sirve para comprobar que la combinación que llega existe de verdad:
     * los nombres viajan por la red y nada impide mandar «Miraflores» dentro de
     * un departamento donde no está.
     */
    @Query("""
            SELECT u FROM Ubigeo u
            WHERE LOWER(u.departamento) = LOWER(:departamento)
              AND LOWER(u.provincia) = LOWER(:provincia)
              AND LOWER(u.distrito) = LOWER(:distrito)
            """)
    Optional<Ubigeo> buscar(@Param("departamento") String departamento,
            @Param("provincia") String provincia,
            @Param("distrito") String distrito);
}
