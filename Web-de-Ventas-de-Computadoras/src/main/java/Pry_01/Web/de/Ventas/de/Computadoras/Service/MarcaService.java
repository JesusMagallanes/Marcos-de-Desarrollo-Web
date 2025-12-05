package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MarcaRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class MarcaService {
    private final MarcaRepository marcaRepository;
    private final CategoriaRepository categoriaRepository;

    public List<MarcaResponseDTO> listarMarca() {
        List<MarcaModel> marcas = marcaRepository.findAll();

        return marcas.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public Page<MarcaResponseDTO> listarPorCategoria(CategoriaModel categoria, Pageable pageable) {
        Page<MarcaModel> marcaPage = marcaRepository.findByCategoriaId(categoria, pageable);
        return marcaPage.map(this::convertToResponseDTO);
    }

    public void eliminarProducto(Long id) {
        marcaRepository.deleteById(id);
    }

    public MarcaModel guardarMarca(MarcaCreateDTO dto) {
        MarcaModel marca = new MarcaModel();
        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
        marca.setName(dto.getName());
        marca.setCategoriaId(categoria);
        return marcaRepository.save(marca);
    }
    public MarcaModel actualizarProducto(Long id, MarcaUpdateDTO dto) {
        MarcaModel marca = marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));
        marca.setName(dto.getName());
        marca.setCategoriaId(categoria);
        return marcaRepository.save(marca);
    }

    public MarcaResponseDTO obtenerProductoPorId(Long id) {
        MarcaModel marca = marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        return convertToResponseDTO(marca);
    }

    private MarcaResponseDTO convertToResponseDTO(MarcaModel marca) {
        return new MarcaResponseDTO(
                marca.getId(),
                marca.getName(),
                marca.getCategoriaId() != null ? marca.getCategoriaId().getName() : null,
                marca.getProductos() != null ? marca.getProductos() : new ArrayList<>());
    }
    public MarcaModel obtenerPorId(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }
}
