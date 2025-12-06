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

    // Listar todas las marcas
    public List<MarcaModel> listarMarcas() {
        return marcaRepository.findAll();
    }

    // Listar marcas por categoría
    public List<MarcaModel> listarMarcasPorCategoria(Long categoriaId) {
        return marcaRepository.findByCategoria_Id(categoriaId);
    }

    // Guardar una nueva marca
    public MarcaModel guardarMarca(MarcaCreateDTO dto) {
        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        MarcaModel marca = new MarcaModel();
        marca.setName(dto.getName());
        marca.setDescripcion(dto.getDescripcion());
        marca.setCategoria(categoria);

        return marcaRepository.save(marca);
    }

    // Actualizar marca existente
    public MarcaModel actualizarMarca(Long id, MarcaUpdateDTO dto) {
        MarcaModel marca = marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));

        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));

        marca.setName(dto.getName());
        marca.setDescripcion(dto.getDescripcion());
        marca.setCategoria(categoria);

        return marcaRepository.save(marca);
    }

    // Eliminar marca
    public void eliminarMarca(Long id) {
        marcaRepository.deleteById(id);
    }

    // Obtener marca por id
    public MarcaModel obtenerPorId(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));
    }
}
