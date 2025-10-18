package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;

@Repository
public interface EnviosRepository extends JpaRepository<EnviosModel, Long>{
    
}
