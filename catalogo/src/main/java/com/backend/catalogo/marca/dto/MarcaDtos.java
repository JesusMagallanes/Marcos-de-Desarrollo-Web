package com.backend.catalogo.marca.dto;

import java.util.List;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.marca.Marca;
import com.backend.catalogo.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MarcaDtos {

    private MarcaDtos() {
    }

    /**
     * La marca, con TODAS sus categorías.
     *
     * <p>{@code categoriaId} se mantiene y devuelve la primera. No es un resto
     * olvidado: el panel de administración y la app móvil lo leen, y cambiarles
     * el contrato en la misma tanda que el modelo de datos habría mezclado dos
     * cosas que conviene poder revertir por separado. Se retirará cuando esas
     * pantallas usen {@code categoriaIds}.
     */
    public record MarcaResponse(
            Long id,
            String name,
            String descripcion,
            Long categoriaId,
            List<Long> categoriaIds) {

        public static MarcaResponse desde(Marca m) {
            List<Long> ids = m.getCategorias().stream().map(Categoria::getId).toList();
            return new MarcaResponse(m.getId(), m.getName(), m.getDescripcion(),
                    ids.isEmpty() ? null : ids.get(0), ids);
        }
    }

    /**
     * Alta y edición. Acepta las dos formas y se queda con la que venga.
     *
     * <p>{@code categoriaIds} es la buena; {@code categoriaId} es lo que sigue
     * mandando el formulario del panel. Aceptar ambas evita tener que desplegar
     * backend y frontend a la vez.
     */
    public record MarcaRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1000) String descripcion,
            @Positive Long categoriaId,
            List<@Positive Long> categoriaIds) {

        /** A03: se limpia antes de validar y antes del control de duplicados. */
        public MarcaRequest {
            name = Saneador.texto(name);
            descripcion = Saneador.textoMultilinea(descripcion);
        }

        /**
         * Las categorías pedidas, vengan como vengan, sin repetidos y en orden.
         *
         * <p>Devuelve lista vacía si no llega ninguna; quien llama decide si eso
         * es un error. Se resuelve aquí y no en el servicio para que exista un
         * único sitio donde las dos formas se unifican.
         */
        public List<Long> categoriasPedidas() {
            if (categoriaIds != null && !categoriaIds.isEmpty()) {
                return categoriaIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            }
            return categoriaId == null ? List.of() : List.of(categoriaId);
        }
    }
}
