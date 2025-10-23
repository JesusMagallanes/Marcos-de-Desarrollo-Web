package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;

@Repository
public interface MetodoPagoRepository extends JpaRepository <MetodoPagoModel, Long> {
    boolean existsByName(String name);
}
