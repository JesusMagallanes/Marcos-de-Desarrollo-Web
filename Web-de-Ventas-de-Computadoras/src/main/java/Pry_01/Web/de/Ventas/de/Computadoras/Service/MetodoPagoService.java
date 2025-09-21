package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MetodoPagoRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class MetodoPagoService {
    private final MetodoPagoRepository metodoPagoRepository;

    public MetodoPagoService(MetodoPagoRepository metodoPagoRepository) {
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public List<MetodoPagoModel> listarMetodosPago() {
        return metodoPagoRepository.findAll();
    }

    public void eliminarMetodoPago(Long id) {
        if (metodoPagoRepository.existsById(id)) {
            metodoPagoRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Producto con ID " + id + " no existe");

        }
    }

    public MetodoPagoModel guardarMetodoPago(MetodoPagoModel metodoPago){
        if (metodoPagoRepository.existsByName(metodoPago.getName())) {
            throw new IllegalArgumentException("El método de pago ya existe");
        }else{
            return metodoPagoRepository.save(metodoPago); 
        }
    }

    
}
