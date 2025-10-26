package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MetodoPagoRepository;

@Service
public class MetodoPagoService {
    private final MetodoPagoRepository metodoPagoRepository;

    public MetodoPagoService(MetodoPagoRepository metodoPagoRepository) {
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public List<MetodoPagoResponseDTO> listarMetodosPago() {
        return metodoPagoRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    public MetodoPagoModel guardarMetodoPago(MetodoPagoCreateDTO dto) {
        MetodoPagoModel metodo = new MetodoPagoModel();
        metodo.setName(dto.getName());
        metodo.setDescription(dto.getDescription());
        return metodoPagoRepository.save(metodo);
    }

    public MetodoPagoResponseDTO obtenerPorId(Long id) {
        MetodoPagoModel metodo = metodoPagoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Metodo de pago no encontrado"));
        return convertToResponseDTO(metodo);
    }

    public MetodoPagoModel actualizarMetodoPago(Long id, MetodoPagoUpdateDTO dto) {
        MetodoPagoModel metodo = metodoPagoRepository.findById(id)
        .orElseThrow(()-> new RuntimeException("Metodo de pago no encontrado"));
        metodo.setName(dto.getName());
        metodo.setDescription(dto.getDescription());
        return metodoPagoRepository.save(metodo);
    }

    public void eliminarMetodoPago(Long id) {
        metodoPagoRepository.deleteById(id);
    }

    private MetodoPagoResponseDTO convertToResponseDTO(MetodoPagoModel metodo) {
        return new MetodoPagoResponseDTO(
                metodo.getId(),
                metodo.getName(),
                metodo.getDescription());
    }

}