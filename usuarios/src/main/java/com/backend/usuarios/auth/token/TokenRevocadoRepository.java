package com.backend.usuarios.auth.token;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    boolean existsByJti(String jti);

    @Modifying
    @Query("DELETE FROM TokenRevocado t WHERE t.expiraEn < :limite")
    int borrarExpirados(@Param("limite") LocalDateTime limite);

    /** Revocación masiva: al eliminar un usuario o degradar su rol. */
    @Modifying
    @Query("DELETE FROM TokenRevocado t WHERE t.usuarioId = :usuarioId")
    int borrarPorUsuario(@Param("usuarioId") Long usuarioId);
}
