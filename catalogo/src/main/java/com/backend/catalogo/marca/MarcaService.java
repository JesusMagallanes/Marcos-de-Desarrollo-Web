package com.backend.catalogo.marca;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

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
        return repositorio.listarConCategorias().stream().map(MarcaResponse::desde).toList();
    }

    public List<MarcaResponse> listarPorCategoria(Long categoriaId) {
        return repositorio.listarPorCategoria(categoriaId).stream()
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
        Marca marca = Marca.builder()
                .name(dto.name())
                .descripcion(dto.descripcion())
                .categorias(resolverCategorias(dto))
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

        // Se reemplaza el contenido en vez del Set entero: Hibernate necesita
        // seguir gestionando la misma coleccion para calcular el delta de la
        // tabla intermedia.
        marca.getCategorias().clear();
        marca.getCategorias().addAll(resolverCategorias(dto));

        return MarcaResponse.desde(repositorio.save(marca));
    }

    /**
     * Traduce los ids de la peticion a categorias existentes.
     *
     * <p>Exige al menos una: una marca que no vende en ninguna categoria no
     * aparece en ningun listado, y crearla asi es casi siempre un formulario a
     * medio enviar, no una intencion.
     */
    private LinkedHashSet<Categoria> resolverCategorias(MarcaRequest dto) {
        List<Long> ids = dto.categoriasPedidas();
        if (ids.isEmpty()) {
            throw new ConflictoException("La marca necesita al menos una categoria");
        }
        return ids.stream()
                .map(categoriaService::buscar)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Marca " + id + " no encontrada");
        }
        repositorio.deleteById(id);
    }
}
