package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDto;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

@Service
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    //LISTAR
    public List<CategoriaModel> listarCategoria() {
        return categoriaRepository.findAll();
    }

    //ELIMINAR
    public void eliminarCategoria(Long id) {
        if (categoriaRepository.existsById(id)) {
            categoriaRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Categoria con ID " + id + " no existe");

        }
    }
    
    //CREAR
    public CategoriaModel crearCategoria(@Valid CategoriaModel categoria) {
        if (categoriaRepository.existsByName(categoria.getName())) {
            throw new IllegalArgumentException("La categoria ya existe");
        } else {
            return categoriaRepository.save(categoria);
        }
    }

    //ACTUALIZAR
    public Optional<CategoriaModel> actualizarCategoria(Long id, @Valid CategoriaDto categoriaDto) {
        Optional<CategoriaModel> categoriaOptional = categoriaRepository.findById(id);
        if (!categoriaOptional.isPresent()) {
            return Optional.empty();
        }

        CategoriaModel categoria = categoriaOptional.get();

        if (categoriaDto.getName() != null) {
            categoria.setName(categoriaDto.getName());
        }
        if (categoriaDto.getDescripcion() != null) {
            categoria.setDescription(categoriaDto.getDescripcion());
        }
        if (categoriaDto.getUrlImage() != null) {
            categoria.setUrlImage(categoriaDto.getUrlImage());
        }
        CategoriaModel CategoriaActualizado = categoriaRepository.save(categoria);
        return Optional.of(CategoriaActualizado);
    }
}