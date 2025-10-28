package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDto;
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

    public Optional<MetodoPagoModel> actualizarMetodoDePago(Long id, MetodoPagoDto metodoPagoDto) {
        Optional<MetodoPagoModel> metodoOptional = metodoPagoRepository.findById(id);
        if (!metodoOptional.isPresent()) {
            return Optional.empty();
        }

        MetodoPagoModel metodoPago = metodoOptional.get();

        if (metodoPagoDto.getName() != null) {
            metodoPago.setName(metodoPagoDto.getName());
        }
        if (metodoPagoDto.getDescripcion() != null) {
            metodoPago.setDescription(metodoPagoDto.getDescripcion());
        }

        MetodoPagoModel metodoPagoActualizado  = metodoPagoRepository.save(metodoPago);
        return Optional.of(metodoPagoActualizado);
        
    }   
}