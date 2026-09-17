package com.backend.catalogo.ia;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.ia.dto.IaDtos.AnalisisModeracion;
import com.backend.catalogo.ia.dto.IaDtos.Riesgo;
import com.backend.catalogo.producto.FiltroProductos;
import com.backend.catalogo.producto.FiltroProductos.Orden;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;
import com.backend.catalogo.valoracion.ValoracionService;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionAdminResponse;

import tools.jackson.databind.JsonNode;

/**
 * Un primer análisis de lo que espera moderación, para quien modera.
 *
 * <h4>Asiste, no decide</h4>
 *
 * <p>No hay ningún camino por el que este análisis apruebe, rechace ni oculte
 * nada. Devuelve un riesgo y unos motivos, y el panel los enseña junto al
 * producto o la reseña. La decisión sigue siendo de una persona con nombre, que
 * es quien queda en la auditoría. Un modelo se equivoca con seguridad, y un
 * rechazo automático injusto a un vendedor no tiene a quién reclamar.
 *
 * <h4>Reglas y modelo, juntos</h4>
 *
 * <p>Lo que se puede comprobar con certeza se comprueba con código: un teléfono o
 * un enlace en la descripción (el vendedor intenta sacar la venta de la tienda),
 * un precio que no encaja con su categoría, un texto que intenta darle órdenes
 * al revisor. Eso sale siempre, haya modelo o no. El modelo aporta lo que las
 * reglas no ven: que la descripción no corresponde al producto, que una reseña
 * de cinco estrellas en realidad es una queja, que un «original» suena a réplica.
 * El riesgo final es el mayor de los dos.
 */
@Service
public class RevisorModeracion {

    static final String FUNCION_PRODUCTO = "moderacion_producto";
    static final String FUNCION_VALORACION = "moderacion_valoracion";

    private static final Pattern CONTACTO = Pattern.compile(
            "(?iu)(https?://|www\\.|\\b[\\w.+-]+@[\\w-]+\\.[a-z]{2,}\\b|\\+?51\\s?9\\d{2}\\s?\\d{3}\\s?\\d{3}"
                    + "|\\b9\\d{8}\\b|whats\\s?app|\\bwsp\\b|telegram)");

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final ProductoService productos;
    private final CategoriaService categorias;
    private final ValoracionService valoraciones;

    public RevisorModeracion(ClienteIa ia, GuardiaPrompt guardia, ProductoService productos,
            CategoriaService categorias, ValoracionService valoraciones) {
        this.ia = ia;
        this.guardia = guardia;
        this.productos = productos;
        this.categorias = categorias;
        this.valoraciones = valoraciones;
    }

    /* ══════════════ Productos de colaborador ══════════════ */

    public AnalisisModeracion producto(Long id) {
        ProductoResponse p = productos.obtener(id);
        String slug = categorias.obtener(p.categoriaId()).slug();
        BigDecimal mediana = medianaDePrecios(slug);

        GuardiaPrompt.Entrada nombre = guardia.limpiar(p.name(), 150);
        GuardiaPrompt.Entrada descripcion = guardia.limpiar(p.description(), 500);
        GuardiaPrompt.Entrada especificaciones = guardia.limpiar(p.specifications(), 2000);

        Hallazgos reglas = new Hallazgos();
        if (nombre.sospechosa() || descripcion.sospechosa() || especificaciones.sospechosa()) {
            reglas.agregar(Riesgo.ALTO,
                    "El texto contiene instrucciones dirigidas a un revisor automático.");
        }
        if (CONTACTO.matcher(p.description() + " " + p.specifications()).find()) {
            reglas.agregar(Riesgo.MEDIO,
                    "Incluye datos de contacto o enlaces externos: puede intentar cerrar la venta fuera de la tienda.");
        }
        if (mediana != null && p.precio() != null) {
            if (p.precio().compareTo(mediana.multiply(new BigDecimal("0.2"))) < 0) {
                reglas.agregar(Riesgo.MEDIO, "Precio muy por debajo de su categoría (mediana S/ "
                        + mediana.round(new MathContext(4)).toPlainString() + "): revisar si es réplica o error.");
            } else if (p.precio().compareTo(mediana.multiply(BigDecimal.valueOf(5))) > 0) {
                reglas.agregar(Riesgo.BAJO, "Precio muy por encima de su categoría (mediana S/ "
                        + mediana.round(new MathContext(4)).toPlainString() + ").");
            }
        }
        if (muchasMayusculas(p.description())) {
            reglas.agregar(Riesgo.BAJO, "Descripción casi toda en mayúsculas.");
        }

        if (reglas.riesgo == Riesgo.ALTO) {
            // No se le da al modelo un texto que intenta manipularlo.
            return reglas.sinIa("Rechazar o pedir al vendedor que reescriba la ficha.");
        }

        String sistema = """
                Revisas productos que vendedores externos quieren publicar en SmartZone, \
                tienda de tecnología de Perú (precios en soles). Una persona tomará la decisión; \
                tú señalas lo que merece su atención.

                Marca como ALTO: falsificaciones o réplicas presentadas como originales, productos \
                que no son tecnología o están prohibidos, descripciones engañosas.
                MEDIO: nombre, descripción, categoría o precio que no encajan entre sí, \
                especificaciones inverosímiles para ese modelo, producto usado sin decirlo.
                BAJO: ficha correcta, quizá mejorable en redacción.
                %s

                Responde SOLO JSON: {"riesgo": "BAJO|MEDIO|ALTO", \
                "motivos": ["máximo 4, frases cortas y concretas"], \
                "sugerencia": "qué haría el moderador, una frase"}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = String.join("\n\n",
                "Categoría: " + p.categoriaName(),
                "Marca: " + (p.marcaName() == null ? "(sin marca)" : p.marcaName()),
                "Precio: S/ " + p.precio().toPlainString()
                        + (mediana == null ? "" : " (mediana de la categoría: S/ " + mediana.toPlainString() + ")"),
                guardia.delimitar("nombre", nombre.texto()),
                guardia.delimitar("descripcion", descripcion.texto()),
                guardia.delimitar("especificaciones", especificaciones.texto()));

        return combinar(reglas, ia.json(FUNCION_PRODUCTO, sistema, usuario, 350),
                "Revisar la ficha antes de aprobar.");
    }

    /* ══════════════ Valoraciones ══════════════ */

    public AnalisisModeracion valoracion(Long id) {
        ValoracionAdminResponse v = valoraciones.obtenerParaModeracion(id);
        GuardiaPrompt.Entrada comentario = guardia.limpiar(v.comentario(), 1500);

        Hallazgos reglas = new Hallazgos();
        if (comentario.sospechosa()) {
            reglas.agregar(Riesgo.ALTO, "El comentario contiene instrucciones dirigidas a un revisor automático.");
            return reglas.sinIa("Rechazar.");
        }
        if (CONTACTO.matcher(v.comentario()).find()) {
            reglas.agregar(Riesgo.MEDIO, "Incluye datos de contacto o enlaces: posible spam o datos personales.");
        }

        String sistema = """
                Revisas reseñas de clientes antes de publicarlas en SmartZone, tienda de tecnología. \
                Una persona decide; tú señalas lo que merece su atención.

                ALTO: insultos, odio, contenido sexual, datos personales de terceros, spam o \
                publicidad de otra tienda.
                MEDIO: la calificación contradice el texto (5 estrellas con quejas o 1 estrella con \
                elogios), el texto no habla de este producto, parece una reseña falsa o pagada.
                BAJO: opinión legítima, aunque sea negativa. Una crítica dura pero honesta es BAJO: \
                no se censuran opiniones.
                %s

                Responde SOLO JSON: {"riesgo": "BAJO|MEDIO|ALTO", \
                "motivos": ["máximo 3, frases cortas"], "sugerencia": "una frase"}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = "Producto: " + v.productoNombre() + "\nCalificación: " + v.calificacion()
                + " de 5\n\n" + guardia.delimitar("resena", comentario.texto());

        return combinar(reglas, ia.json(FUNCION_VALORACION, sistema, usuario, 250),
                "Publicar si no hay motivos de peso.");
    }

    /* ══════════════ Piezas ══════════════ */

    private AnalisisModeracion combinar(Hallazgos reglas, Optional<JsonNode> respuesta,
            String sugerenciaPorDefecto) {
        if (respuesta.isEmpty()) {
            return reglas.sinIa(sugerenciaPorDefecto);
        }
        JsonNode r = respuesta.get();
        LinkedHashSet<String> motivos = new LinkedHashSet<>(reglas.motivos);
        motivos.addAll(Respuestas.lista(r, "motivos", 4, 180));
        String sugerencia = Respuestas.texto(r, "sugerencia", 220);
        return new AnalisisModeracion(
                Respuestas.mayor(reglas.riesgo, Respuestas.riesgo(r)),
                List.copyOf(motivos).subList(0, Math.min(motivos.size(), 6)),
                sugerencia.isEmpty() ? sugerenciaPorDefecto : sugerencia,
                true);
    }

    private BigDecimal medianaDePrecios(String slug) {
        List<BigDecimal> precios = productos.buscar(null, slug,
                        new FiltroProductos(null, null, List.of(), List.of(), false, Orden.PRECIO_ASC),
                        0, 60)
                .content().stream()
                .map(ProductoResponse::precio)
                .toList();
        // Con menos de cinco productos una mediana no dice nada.
        return precios.size() < 5 ? null : precios.get(precios.size() / 2);
    }

    private static boolean muchasMayusculas(String texto) {
        if (texto == null) {
            return false;
        }
        long letras = texto.chars().filter(Character::isLetter).count();
        long mayusculas = texto.chars().filter(Character::isUpperCase).count();
        return letras > 30 && mayusculas * 10 > letras * 6;
    }

    /** Lo que encuentran las reglas, acumulado. */
    private static final class Hallazgos {
        private Riesgo riesgo = Riesgo.BAJO;
        private final List<String> motivos = new ArrayList<>();

        void agregar(Riesgo nivel, String motivo) {
            riesgo = Respuestas.mayor(riesgo, nivel);
            motivos.add(motivo);
        }

        AnalisisModeracion sinIa(String sugerencia) {
            return new AnalisisModeracion(riesgo, List.copyOf(motivos), sugerencia, false);
        }
    }
}
