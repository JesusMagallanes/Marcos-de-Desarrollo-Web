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
                .padre(dto.padreId() == null ? null : buscar(dto.padreId()))
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
        categoria.setPadre(resolverPadre(categoria, dto.padreId()));

        return CategoriaResponse.desde(repositorio.save(categoria));
    }

    /**
     * El padre pedido, comprobando que no se cierre un ciclo.
     *
     * <p>Un ciclo deja el arbol irrecorrible: pintar las migas de pan o bajar
     * por los hijos se convierte en un bucle infinito. La base atrapa el caso de
     * un solo nodo (`ck_categoria_no_es_su_padre`), pero no el de A -> B -> A,
     * que necesita recorrer la cadena y por eso se comprueba aqui.
     */
    private Categoria resolverPadre(Categoria categoria, Long padreId) {
        if (padreId == null) {
            return null;
        }
        if (padreId.equals(categoria.getId())) {
            throw new ConflictoException("Una categoria no puede colgar de si misma");
        }

        Categoria padre = buscar(padreId);
        for (Categoria a = padre; a != null; a = a.getPadre()) {
            if (a.getId().equals(categoria.getId())) {
                throw new ConflictoException(
                        "Ese movimiento dejaria un ciclo: '%s' ya cuelga de '%s'"
                                .formatted(padre.getName(), categoria.getName()));
            }
        }
        return padre;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Categoría " + id + " no encontrada");
        }
        /*
         * Una categoria con hijas no se borra.
         *
         * La FK lo impediria igualmente, pero devolviendo un 500 con un error de
         * Postgres. Aqui se responde 409 diciendo QUE pasa y cuantas cuelgan, que
         * es lo que necesita quien esta en el panel. Y no se borra en cascada a
         * proposito: llevarse un subarbol entero por un clic es justo el tipo de
         * borrado que nadie deshace.
         */
        long hijas = repositorio.countByPadreId(id);
        if (hijas > 0) {
            throw new ConflictoException(
                    "No se puede eliminar: hay %d categoria(s) colgando de esta. Muevelas o borralas antes."
                            .formatted(hijas));
        }
        // La FK lo impediría igualmente, pero así el mensaje es claro.
        repositorio.deleteById(id);
    }
}
