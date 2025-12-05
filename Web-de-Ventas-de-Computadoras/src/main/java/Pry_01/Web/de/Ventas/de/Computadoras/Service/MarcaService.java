package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MarcaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarcaService {

    private final MarcaRepository marcaRepository;
    private final CategoriaRepository categoriaRepository;

    public List<MarcaModel> listarMarcas() {
        return marcaRepository.findAll();
    }

    public MarcaModel guardarMarca(MarcaCreateDTO dto) {
        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        MarcaModel marca = new MarcaModel();
        marca.setNombre(dto.getNombre());
        marca.setCategoria(categoria);

        return marcaRepository.save(marca);
    }

    public MarcaModel obtenerPorId(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));
    }

    public void eliminarMarca(Long id) {
        marcaRepository.deleteById(id);
    }

    public MarcaModel actualizarMarca(Long id, MarcaUpdateDTO dto) {
        MarcaModel marca = marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));

        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        marca.setNombre(dto.getNombre());
        marca.setCategoria(categoria);

        return marcaRepository.save(marca);
    }

    public List<MarcaModel> listarMarcasPorCategoria(Long categoriaId) {
        CategoriaModel categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        return marcaRepository.findByCategoria(categoria);
    }
}
