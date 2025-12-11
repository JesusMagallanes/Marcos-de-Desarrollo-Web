package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvioRepository extends JpaRepository<EnviosModel, Long> {
    
    // Buscar envíos por estado (el campo en EnviosModel es "estadoEnvio")
    List<EnviosModel> findByEstadoEnvio(EstadoEnvio estado);
    
    // Buscar envíos por múltiples estados
    List<EnviosModel> findByEstadoEnvioIn(List<EstadoEnvio> estados);
    
    // Buscar envío por pedido
    Optional<EnviosModel> findByPedidoId(Long pedidoId);
    
    // Buscar envíos por usuario (a través del pedido)
    @Query("SELECT e FROM EnviosModel e WHERE e.pedido.usuario.id = :userId")
    List<EnviosModel> findByUsuarioId(@Param("userId") Long userId);
    
    // Contar envíos por estado
    Long countByEstadoEnvio(EstadoEnvio estado);
}