package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoItemModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;

@Repository
public interface CarritoItemRepository extends JpaRepository <CarritoItemModel, Long> {
    Optional<CarritoItemModel> findByCarritoAndProducto(CarritoModel carrito, ProductoModel producto);
}
