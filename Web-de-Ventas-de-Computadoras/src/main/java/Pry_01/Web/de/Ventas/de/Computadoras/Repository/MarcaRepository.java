package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;

@Repository
public interface MarcaRepository extends JpaRepository<MarcaModel, Long> {

    // Verifica si ya existe una marca con ese nombre
    boolean existsByName(String name);

    // Lista todas las marcas asociadas a una categoría
    List<MarcaModel> findByCategoria_Id(Long categoriaId);
}