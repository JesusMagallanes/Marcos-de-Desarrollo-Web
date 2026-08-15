package com.backend.usuarios.auth.token;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    boolean existsByJti(String jti);

    /*
     * Los dos borrados llevan su propia @Transactional en vez de confiar en la
     * de quien llama. Un @Modifying sin transacción falla, y hasta ahora
     * funcionaba solo porque todos los llamantes eran transaccionales: es una
     * dependencia invisible que se rompe en cuanto alguien llama desde una tarea
     * programada. Aquí la transacción es de la propia operación, que es donde
     * debe estar; si el llamante ya tiene una abierta, se une a ella.
     */

    @Modifying
    @Transactional
    @Query("DELETE FROM TokenRevocado t WHERE t.expiraEn < :limite")
    int borrarExpirados(@Param("limite") LocalDateTime limite);

    /** Revocación masiva: al eliminar un usuario o degradar su rol. */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenRevocado t WHERE t.usuarioId = :usuarioId")
    int borrarPorUsuario(@Param("usuarioId") Long usuarioId);
}
