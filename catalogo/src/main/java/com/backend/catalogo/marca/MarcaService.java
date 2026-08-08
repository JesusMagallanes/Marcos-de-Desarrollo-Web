package com.backend.catalogo.marca;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.marca.dto.MarcaDtos.MarcaRequest;
import com.backend.catalogo.marca.dto.MarcaDtos.MarcaResponse;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarcaService {

    private final MarcaRepository repositorio;
    private final CategoriaService categoriaService;

    public List<MarcaResponse> listar() {
        return repositorio.findAllByOrderByNameAsc().stream().map(MarcaResponse::desde).toList();
    }

    public List<MarcaResponse> listarPorCategoria(Long categoriaId) {
        return repositorio.findByCategoriaIdOrderByNameAsc(categoriaId).stream()
                .map(MarcaResponse::desde)
                .toList();
    }

    public Marca buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Marca " + id + " no encontrada"));
    }

    @Transactional
    public MarcaResponse crear(MarcaRequest dto) {
        if (repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe una marca con ese nombre");
        }
        Categoria categoria = categoriaService.buscar(dto.categoriaId());

        Marca marca = Marca.builder()
                .name(dto.name())
                .descripcion(dto.descripcion())
                .categoria(categoria)
                .build();

        return MarcaResponse.desde(repositorio.save(marca));
    }

    @Transactional
    public MarcaResponse actualizar(Long id, MarcaRequest dto) {
        Marca marca = buscar(id);

        if (!marca.getName().equals(dto.name()) && repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe una marca con ese nombre");
        }

        marca.setName(dto.name());
        marca.setDescripcion(dto.descripcion());
        marca.setCategoria(categoriaService.buscar(dto.categoriaId()));

        return MarcaResponse.desde(repositorio.save(marca));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Marca " + id + " no encontrada");
        }
        repositorio.deleteById(id);
    }
}
