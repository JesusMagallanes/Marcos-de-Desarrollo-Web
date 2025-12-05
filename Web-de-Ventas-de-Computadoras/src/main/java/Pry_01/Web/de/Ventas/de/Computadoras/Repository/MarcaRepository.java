package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;

@Repository
public interface MarcaRepository extends JpaRepository<MarcaModel, Long> {
    boolean existsByName(String name);

    Page<MarcaModel> findByCategoriaId(CategoriaModel categoria, Pageable pageable);
    
    List<MarcaModel> findByCategoriaId(CategoriaModel categoria);
}
