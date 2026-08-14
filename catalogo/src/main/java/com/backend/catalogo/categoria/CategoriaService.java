package com.backend.catalogo.categoria;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaRequest;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoriaService {

    private final CategoriaRepository repositorio;

    public List<CategoriaResponse> listar() {
        return repositorio.findAllByOrderByNameAsc().stream().map(CategoriaResponse::desde).toList();
    }

    public CategoriaResponse obtener(Long id) {
        return CategoriaResponse.desde(buscar(id));
    }

    public CategoriaResponse obtenerPorSlug(String slug) {
        return repositorio.findBySlug(slug)
                .map(CategoriaResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría '" + slug + "' no encontrada"));
    }

    /** Comprobación que NO lanza. */
    public boolean existePorSlug(String slug) {
        return slug != null && repositorio.findBySlug(slug).isPresent();
    }

    public Categoria buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría " + id + " no encontrada"));
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest dto) {
        if (repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe una categoría con ese nombre");
        }
        if (repositorio.existsBySlug(dto.slug())) {
            throw new ConflictoException("Ya existe una categoría con ese slug");
        }

        Categoria categoria = Categoria.builder()
                .name(dto.name())
                .slug(dto.slug())
                .description(dto.description())
                .icono(dto.icono())
                .build();

        return CategoriaResponse.desde(repositorio.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest dto) {
        Categoria categoria = buscar(id);

        if (!categoria.getName().equals(dto.name()) && repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe una categoría con ese nombre");
        }
        if (!categoria.getSlug().equals(dto.slug()) && repositorio.existsBySlug(dto.slug())) {
            throw new ConflictoException("Ya existe una categoría con ese slug");
        }

        categoria.setName(dto.name());
        categoria.setSlug(dto.slug());
        categoria.setDescription(dto.description());
        categoria.setIcono(dto.icono());

        return CategoriaResponse.desde(repositorio.save(categoria));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Categoría " + id + " no encontrada");
        }
        // La FK lo impediría igualmente, pero así el mensaje es claro.
        repositorio.deleteById(id);
    }
}
