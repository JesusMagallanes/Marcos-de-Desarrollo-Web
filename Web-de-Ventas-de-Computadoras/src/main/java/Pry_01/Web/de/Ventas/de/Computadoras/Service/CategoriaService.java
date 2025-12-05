package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public List<CategoriaModel> listarCategoria() {
        return categoriaRepository.findAll();
    }

    public CategoriaModel guardarCategoria(CategoriaCreateDTO dto) {
        CategoriaModel categoria = new CategoriaModel();
        categoria.setName(dto.getName());
        categoria.setUrlImage(dto.getUrlImage());
        categoria.setDescription(dto.getDescription());
        categoria.setSlug(generarSlug(dto.getName()));
        return categoriaRepository.save(categoria);
    }

    public CategoriaModel obtenerPorId(Long id) {
        return categoriaRepository.findById(id).orElse(null);
    }

    public void eliminarCategoria(Long id) {
        categoriaRepository.deleteById(id);
    }

    public void actualizarCategoria(Long id, CategoriaUpdateDTO dto) {
        CategoriaModel categoria = obtenerPorId(id);
        if (categoria != null) {
            categoria.setName(dto.getName());
            categoria.setUrlImage(dto.getUrlImage());
            categoria.setDescription(dto.getDescription());
            categoriaRepository.save(categoria);
        }
    }

    public String generarSlug(String nombre) {
        return nombre.toLowerCase()
                .replaceAll("[áéíóú]", "a")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public CategoriaModel obtenerPorSlug(String slug) {
        return categoriaRepository.findBySlug(slug).orElse(null);
    }
}
