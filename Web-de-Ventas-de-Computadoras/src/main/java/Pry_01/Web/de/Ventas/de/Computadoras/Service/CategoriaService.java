package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public List<CategoriaModel> listarCategoria() {
        return categoriaRepository.findAll();
    }

    public void eliminarCategoria(Long id) {
        if (categoriaRepository.existsById(id)) {
            categoriaRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Categoria con ID " + id + " no existe");

        }
    }

    public CategoriaModel guardarCategoria(CategoriaModel categoria){
        if (categoriaRepository.existsByName(categoria.getName())) {
            throw new IllegalArgumentException("La categoria ya existe");
        }else{
            return categoriaRepository.save(categoria); 
        }
    }
}
