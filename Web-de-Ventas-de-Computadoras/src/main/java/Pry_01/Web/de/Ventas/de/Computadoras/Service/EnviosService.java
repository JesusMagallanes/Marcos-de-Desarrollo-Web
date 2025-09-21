package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.EnviosRepository;
import jakarta.persistence.EntityNotFoundException;

public class EnviosService {
    private final EnviosRepository enviosRepository;

    public EnviosService(EnviosRepository enviosRepository) {
        this.enviosRepository = enviosRepository;
    }

    public List<EnviosModel> listarEnvios() {
        return enviosRepository.findAll();
    }

    public void eliminarEnvio(Long id) {
        if (enviosRepository.existsById(id)) {
            enviosRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Envio con ID " + id + " no existe");

        }
    }

    public EnviosModel guardarEnvios(EnviosModel envios){
        if (enviosRepository.existsById(envios.getId())) {
            throw new IllegalArgumentException("El Envío ya existe");
        }else{
            return enviosRepository.save(envios); 
        }
    }

}
