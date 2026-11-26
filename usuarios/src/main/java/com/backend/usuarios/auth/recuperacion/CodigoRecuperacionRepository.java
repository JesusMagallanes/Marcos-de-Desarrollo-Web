package com.backend.usuarios.auth.recuperacion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

    /** El último código emitido a la cuenta: es el único que puede valer. */
    Optional<CodigoRecuperacion> findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId);

    /** Los que aún no se usaron, para anularlos al emitir uno nuevo. */
    List<CodigoRecuperacion> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);
}
