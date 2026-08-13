package com.backend.catalogo.guia;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.guia.dto.GuiaDtos.GuiaRequest;
import com.backend.catalogo.guia.dto.GuiaDtos.GuiaResponse;
import com.backend.catalogo.guia.dto.GuiaDtos.GuiaResumen;
import com.backend.catalogo.guia.dto.GuiaDtos.PasoRequest;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuiaService {

    private final GuiaRepository repositorio;

    /** Lo que ve la tienda: solo guías publicadas. */
    public List<GuiaResumen> listarPublicadas() {
        return repositorio.findByPublicadaTrueOrderByPosicionAscTituloAsc()
                .stream().map(GuiaResumen::desde).toList();
    }

    /** Lo que ve el panel: también los borradores. */
    public List<GuiaResumen> listarTodas() {
        return repositorio.findAllByOrderByPosicionAscTituloAsc()
                .stream().map(GuiaResumen::desde).toList();
    }

    /**
     * Una guía sin publicar responde 404 al público. Se devuelve "no encontrada"
     * y no "prohibida" a propósito: un 403 confirmaría que ese borrador existe.
     */
    public GuiaResponse obtenerPublicada(String slug) {
        Guia guia = buscarPorSlug(slug);
        if (!Boolean.TRUE.equals(guia.getPublicada())) {
            throw new RecursoNoEncontradoException("Guía '" + slug + "' no encontrada");
        }
        return GuiaResponse.desde(guia);
    }

    /** Para el panel: devuelve la guía esté publicada o no. */
    public GuiaResponse obtenerParaEdicion(String slug) {
        return GuiaResponse.desde(buscarPorSlug(slug));
    }

    @Transactional
    public GuiaResponse crear(GuiaRequest dto) {
        if (repositorio.existsBySlug(dto.slug())) {
            throw new ConflictoException("Ya existe una guía con ese slug");
        }

        Instant ahora = Instant.now();
        Guia guia = Guia.builder()
                .slug(dto.slug())
                .titulo(dto.titulo())
                .resumen(dto.resumen())
                .icono(dto.icono())
                .posicion(dto.posicion())
                .publicada(dto.publicada())
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build();
        aplicarPasos(guia, dto.pasos());

        return GuiaResponse.desde(repositorio.save(guia));
    }

    @Transactional
    public GuiaResponse actualizar(Long id, GuiaRequest dto) {
        Guia guia = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Guía " + id + " no encontrada"));

        if (!guia.getSlug().equals(dto.slug()) && repositorio.existsBySlug(dto.slug())) {
            throw new ConflictoException("Ya existe una guía con ese slug");
        }

        guia.setSlug(dto.slug());
        guia.setTitulo(dto.titulo());
        guia.setResumen(dto.resumen());
        guia.setIcono(dto.icono());
        guia.setPosicion(dto.posicion());
        guia.setPublicada(dto.publicada());
        guia.setActualizadoEn(Instant.now());
        aplicarPasos(guia, dto.pasos());

        return GuiaResponse.desde(repositorio.save(guia));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Guía " + id + " no encontrada");
        }
        repositorio.deleteById(id);
    }

    /**
     * Reemplaza los pasos REUTILIZANDO las filas existentes.
     *
     * No es un capricho: hay un índice único sobre (guia_id, posicion) y, dentro
     * de un mismo flush, Hibernate emite los INSERT antes que los DELETE. Un
     * `clear()` seguido de `add()` choca contra ese índice y la edición muere con
     * un 409. Es exactamente el fallo que ya se corrigió en las imágenes de
     * producto, así que aquí se hace bien desde el principio.
     */
    private void aplicarPasos(Guia guia, List<PasoRequest> nuevos) {
        List<GuiaPaso> actuales = guia.getPasos();

        for (int i = 0; i < nuevos.size(); i++) {
            PasoRequest paso = nuevos.get(i);
            if (i < actuales.size()) {
                GuiaPaso existente = actuales.get(i);
                existente.setTitulo(paso.titulo());
                existente.setDescripcion(paso.descripcion());
                existente.setPosicion(i);
            } else {
                actuales.add(GuiaPaso.builder()
                        .guia(guia)
                        .posicion(i)
                        .titulo(paso.titulo())
                        .descripcion(paso.descripcion())
                        .build());
            }
        }

        // Los que sobran desaparecen por orphanRemoval.
        if (actuales.size() > nuevos.size()) {
            actuales.subList(nuevos.size(), actuales.size()).clear();
        }
    }

    private Guia buscarPorSlug(String slug) {
        return repositorio.buscarPorSlugConPasos(slug)
                .orElseThrow(() -> new RecursoNoEncontradoException("Guía '" + slug + "' no encontrada"));
    }
}
