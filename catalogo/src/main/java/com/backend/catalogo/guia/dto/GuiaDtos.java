package com.backend.catalogo.guia.dto;

import java.util.List;

import com.backend.catalogo.guia.Guia;
import com.backend.catalogo.guia.GuiaPaso;
import com.backend.catalogo.shared.validacion.Saneador;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class GuiaDtos {

    private GuiaDtos() {
    }

    /** Lo que necesita la tarjeta del listado; sin los pasos, que no se pintan ahí. */
    public record GuiaResumen(
            Long id,
            String slug,
            String titulo,
            String resumen,
            String icono,
            Integer posicion,
            Boolean publicada,
            int totalPasos) {

        public static GuiaResumen desde(Guia g) {
            return new GuiaResumen(g.getId(), g.getSlug(), g.getTitulo(), g.getResumen(),
                    g.getIcono(), g.getPosicion(), g.getPublicada(), g.getPasos().size());
        }
    }

    public record PasoResponse(Long id, Integer posicion, String titulo, String descripcion) {

        public static PasoResponse desde(GuiaPaso p) {
            return new PasoResponse(p.getId(), p.getPosicion(), p.getTitulo(), p.getDescripcion());
        }
    }

    public record GuiaResponse(
            Long id,
            String slug,
            String titulo,
            String resumen,
            String icono,
            Integer posicion,
            Boolean publicada,
            List<PasoResponse> pasos) {

        public static GuiaResponse desde(Guia g) {
            return new GuiaResponse(g.getId(), g.getSlug(), g.getTitulo(), g.getResumen(),
                    g.getIcono(), g.getPosicion(), g.getPublicada(),
                    g.getPasos().stream().map(PasoResponse::desde).toList());
        }
    }

    /** Un paso tal y como llega del panel. La posición la asigna el servicio. */
    public record PasoRequest(
            @NotBlank @Size(max = 160) String titulo,
            @NotBlank @Size(max = 2000) String descripcion) {

        public PasoRequest {
            titulo = Saneador.texto(titulo);
            descripcion = Saneador.textoMultilinea(descripcion);
        }
    }

    /**
     * Los topes coinciden con los de las columnas: si no, una cadena larga no la
     * rechaza la validación sino Postgres, y el admin recibe un 500 en vez de un
     * 400 que le diga qué campo arreglar.
     */
    public record GuiaRequest(
            @NotBlank @Size(max = 140)
            @Pattern(regexp = "^[a-z0-9-]+$",
                    message = "El slug solo admite minúsculas, números y guiones") String slug,
            @NotBlank @Size(max = 160) String titulo,
            @NotBlank @Size(max = 300) String resumen,
            @Size(max = 60)
            @Pattern(regexp = "^[a-z0-9-]*$",
                    message = "El icono solo admite minúsculas, números y guiones") String icono,
            @NotNull @Min(0) @Max(999) Integer posicion,
            @NotNull Boolean publicada,
            @NotEmpty(message = "La guía necesita al menos un paso")
            @Size(max = 30, message = "Como máximo 30 pasos")
            List<@Valid PasoRequest> pasos) {

        /** A03: se limpia antes de validar y antes del control de slug duplicado. */
        public GuiaRequest {
            slug = Saneador.texto(slug);
            titulo = Saneador.texto(titulo);
            resumen = Saneador.textoMultilinea(resumen);
            icono = Saneador.textoONulo(icono);
            pasos = pasos == null ? List.of() : pasos;
        }
    }
}
