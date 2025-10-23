package Pry_01.Web.de.Ventas.de.Computadoras.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioModel, Long> {
     Optional<UsuarioModel> findByEmailAddress(String emailAddress);
     boolean existsByEmailAddress(String emailAddress);
}
