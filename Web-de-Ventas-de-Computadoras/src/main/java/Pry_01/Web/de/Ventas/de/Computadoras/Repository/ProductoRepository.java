package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;

@Repository
public interface ProductoRepository extends JpaRepository<ProductoModel, Long> {

    boolean existsByName(String name);

    // Filtra productos por categoría
    Page<ProductoModel> findByCategoriaId(CategoriaModel categoria, Pageable pageable);
    List<ProductoModel> findByCategoriaId(CategoriaModel categoria);

    // Filtra productos por marca
    Page<ProductoModel> findByMarca(MarcaModel marca, Pageable pageable);
    List<ProductoModel> findByMarca(MarcaModel marca);
}