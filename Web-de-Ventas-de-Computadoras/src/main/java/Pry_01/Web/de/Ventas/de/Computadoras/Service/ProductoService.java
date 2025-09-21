package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.ProductoRepository;
import jakarta.persistence.EntityNotFoundException;


@Service
public class ProductoService {
    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository){
        this.productoRepository = productoRepository;
    }

    public List<ProductoModel> listarProductos(){
        return productoRepository.findAll();
    }

    public void eliminarProductoPorId(Long id){
        if (productoRepository.existsById(id)) {
            productoRepository.deleteById(id);   
        }else {
            throw new EntityNotFoundException("Producto con ID " + id + " no existe");
        }
    }

    public ProductoModel guardarProducto(ProductoModel producto){
        if (productoRepository.existsByName(producto.getName())) {
            throw new IllegalArgumentException("La producto ya existe");
        }else{
            return productoRepository.save(producto);
        }
    }

    
}
