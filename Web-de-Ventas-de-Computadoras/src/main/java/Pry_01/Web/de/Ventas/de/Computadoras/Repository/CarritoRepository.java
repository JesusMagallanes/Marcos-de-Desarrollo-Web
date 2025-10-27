package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;

@Repository
public interface CarritoRepository extends JpaRepository <CarritoModel, Long>{
    Optional<CarritoModel> findByUsuario(UsuarioModel usuario);
}
