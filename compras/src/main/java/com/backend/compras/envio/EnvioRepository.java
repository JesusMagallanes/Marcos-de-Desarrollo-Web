package com.backend.compras.envio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvioRepository extends JpaRepository<Envio, Long> {

    List<Envio> findByEstadoEnvioOrderByIdDesc(EstadoEnvio estado);

    Optional<Envio> findByPedidoId(Long pedidoId);

    /**
     * En el monolito esto era `e.pedido.usuario.id`, un join de tres tablas que
     * habría cruzado tres servicios. Ahora `usuario_id` es una columna de
     * pedido, así que la consulta se resuelve dentro de `compras`.
     */
    @Query("SELECT e FROM Envio e WHERE e.pedido.usuarioId = :usuarioId ORDER BY e.id DESC")
    List<Envio> listarPorUsuario(@Param("usuarioId") Long usuarioId);
}
