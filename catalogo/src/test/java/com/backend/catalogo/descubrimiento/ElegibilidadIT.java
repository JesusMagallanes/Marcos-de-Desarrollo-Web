package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;

/**
 * Que lo no publicable no llegue nunca a ser candidato.
 *
 * <h4>Cómo se demuestra el ORDEN, y no solo el resultado</h4>
 *
 * <p>Comprobar que un producto agotado no sale del carrusel no basta: podría no
 * salir porque se filtró al final, después de ordenarlo. Y eso sería frágil —
 * cualquier cambio futuro en el ranking podría colarlo.
 *
 * <p>La prueba del orden está en {@code recomendacion_servida}. Ahí se anota lo
 * que el sistema DECIDIÓ servir, y se anota sobre la lista ya elegida. Si la
 * elegibilidad corriera después del ranker, el producto habría entrado en esa
 * lista y habría quedado anotado. Que no esté anotado significa que nunca llegó
 * a ser candidato:
 *
 * <pre>
 *   candidatos → elegibilidad → ranker      (lo que se comprueba)
 *   candidatos → ranker → elegibilidad      (dejaría rastro en servida)
 * </pre>
 *
 * <p>NO es transaccional: el registro escribe en transacción propia y desde una
 * prueba con reversión no vería el sujeto.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Elegibilidad del candidato")
class ElegibilidadIT extends PruebaIntegracion {

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private ElegibilidadService elegibilidad;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private TendenciaService tendencias;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID sujeto;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.tendencia_item");
        jdbc.update("DELETE FROM catalogo.item_relacion");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("elegibilidad-it-" + UUID.randomUUID());
        sujeto = crearSujeto();
    }

    /**
     * Se lleva sus productos al terminar.
     *
     * <p>Esta clase no puede ser transaccional —el registro escribe en
     * transacción propia y no vería un sujeto sin confirmar—, así que lo que
     * crea SE QUEDA en el contenedor, que es uno para toda la ejecución.
     *
     * <p>No es manía de limpieza: dejar productos detrás agranda el catálogo
     * elegible, que es el DENOMINADOR de la cobertura, y hunde esa métrica en
     * {@code EvaluacionOfflineIT}. Costó un fallo entenderlo: 0,0952 contra un
     * umbral de 0,10, sin que nada del recomendador hubiera cambiado.
     */
    @AfterEach
    void devolverElCatalogoComoEstaba() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida"
                + " WHERE item_id IN (SELECT id FROM catalogo.producto WHERE categoria_id = ?)",
                categoria);
        jdbc.update("DELETE FROM catalogo.tendencia_item"
                + " WHERE item_id IN (SELECT id FROM catalogo.producto WHERE categoria_id = ?)",
                categoria);
        jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", categoria);
        jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", categoria);
    }

    /* ══════════════ El agujero que se tapó ══════════════ */

    @Test
    @DisplayName("un producto agotado tras el recálculo horario ya no se recomienda")
    void laTendenciaNoSirveLoQueSeAgoto() {
        /*
         * El caso real. `tendencia_item` se recalcula cada hora y guarda
         * identificadores; entre pasada y pasada el mundo cambia. Antes de esto,
         * quien pulsaba el carrusel se encontraba una ficha sin stock.
         */
        Long vivo = crearProducto("Sigue disponible");
        Long agotado = crearProducto("Se agoto a las 10:05");
        enTendencia(vivo, 9.0);
        enTendencia(agotado, 10.0);

        // Se agota DESPUÉS de que el lote lo declarara tendencia.
        jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", agotado);

        Carrusel carrusel = recomendador.tendenciasDeZona(sujeto, null, 12, new LinkedHashSet<>());

        assertThat(idsDe(carrusel))
                .as("el que se agotó no puede salir, aunque encabezara la tendencia")
                .containsExactly(vivo);
    }

    @Test
    @DisplayName("y tampoco queda anotado como servido: no llegó a ser candidato")
    void loNoElegibleNoDejaRastroDeHaberSidoCandidato() {
        /*
         * Esta es la prueba del ORDEN, no del resultado. Si la elegibilidad
         * corriera después del ranker, el producto habría entrado en la lista
         * elegida y `anotar` lo habría registrado. Que no esté demuestra que se
         * quedó fuera antes.
         */
        Long vivo = crearProducto("Sigue disponible");
        Long agotado = crearProducto("Agotado");
        enTendencia(vivo, 9.0);
        enTendencia(agotado, 10.0);
        jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", agotado);

        recomendador.tendenciasDeZona(sujeto, null, 12, new LinkedHashSet<>());

        assertThat(servidas.findAll()).extracting(RecomendacionServida::getItemId)
                .as("servida = «el backend decidió enseñarlo»; no lo decidió")
                .doesNotContain(agotado)
                .contains(vivo);
    }

    @Test
    @DisplayName("lo que un moderador retira desaparece igual que lo agotado")
    void laModeracionTambienSeRespeta() {
        Long vivo = crearProducto("Aprobado");
        Long retirado = crearProducto("Retirado por moderacion");
        enTendencia(vivo, 9.0);
        enTendencia(retirado, 10.0);

        // Un producto de colaborador puede pasar a RECHAZADO en cualquier momento.
        jdbc.update("UPDATE catalogo.producto SET propietario_id = 99,"
                + " estado_moderacion = 'RECHAZADO', motivo_rechazo = 'IT' WHERE id = ?", retirado);

        assertThat(idsDe(recomendador.tendenciasDeZona(sujeto, null, 12, new LinkedHashSet<>())))
                .containsExactly(vivo);
    }

    @Test
    @DisplayName("si todo lo que era tendencia deja de serlo, no hay carrusel vacío")
    void sinNadaElegibleNoSeSirveUnCarruselHueco() {
        Long unico = crearProducto("El unico");
        enTendencia(unico, 10.0);
        jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", unico);

        assertThat(recomendador.tendenciasDeZona(sujeto, null, 12, new LinkedHashSet<>()))
                .as("mejor ningún carrusel que uno con cero tarjetas")
                .isNull();
    }

    /* ══════════════ El resto de generadores ══════════════ */

    @Test
    @DisplayName("los generadores por SQL ya filtraban, y se comprueba")
    void losOtrosGeneradoresSiguenFiltrando() {
        /*
         * Los seis filtran dentro de su consulta. No se toca nada de eso; esto
         * existe para que se note si alguien lo quita, porque el agujero de la
         * tendencia se abrió exactamente así: alguien escribió una ruta que no
         * pasaba por el filtro.
         */
        Long vivo = crearProducto("Disponible");
        Long agotado = crearProducto("Agotado");
        jdbc.update("UPDATE catalogo.producto SET stock = 0 WHERE id = ?", agotado);

        /*
         * Solo la afirmacion negativa, que es la propiedad bajo prueba. Exigir
         * ademas que el producto recien creado APAREZCA seria atarse al
         * ranking: `populares` ordena por valoracion y completitud de ficha, y
         * uno creado aqui —sin imagenes, sin atributos, sin valoraciones— pierde
         * contra los sesenta y ocho de la semilla. La prueba fallaria por un
         * motivo que no tiene nada que ver con la elegibilidad.
         */
        assertThat(idsDe(recomendador.populares(sujeto, 12, new LinkedHashSet<>())))
                .doesNotContain(agotado);

        // En la ficha si se ve el efecto directo: el hermano de categoria
        // agotado no puede salir entre los relacionados del que se esta viendo.
        assertThat(idsDe(recomendador.similares(vivo, sujeto, 12)))
                .doesNotContain(agotado);
    }

    /* ══════════════ La capa en sí ══════════════ */

    @Test
    @DisplayName("conserva el orden que le dieron")
    void elOrdenSeRespeta() {
        /*
         * Quien llama ya decidió una prelación —la tendencia llega ordenada por
         * su score—. Reordenar aquí convertiría una comprobación en una decisión
         * de ranking, que no es su trabajo.
         */
        Long a = crearProducto("A");
        Long b = crearProducto("B");
        Long c = crearProducto("C");

        assertThat(elegibilidad.filtrar(List.of(c, a, b))).containsExactly(c, a, b);
    }

    @Test
    @DisplayName("con la lista vacía no pregunta nada")
    void listaVacia() {
        assertThat(elegibilidad.filtrar(List.of())).isEmpty();
        assertThat(elegibilidad.filtrar(null)).isEmpty();
    }

    @Test
    @DisplayName("un identificador que ya no existe se cae solo")
    void elProductoBorradoNoSobrevive() {
        Long vivo = crearProducto("Vivo");
        assertThat(elegibilidad.filtrar(List.of(vivo, -999L))).containsExactly(vivo);
    }

    /* ══════════════ Utilidades ══════════════ */

    private List<Long> idsDe(Carrusel carrusel) {
        return carrusel == null ? List.of() : carrusel.items().stream().map(p -> p.id()).toList();
    }

    /** Mete un producto en la tendencia nacional, como haría el proceso horario. */
    private void enTendencia(Long producto, double score) {
        jdbc.update("INSERT INTO catalogo.tendencia_item"
                + " (nivel, zona, item_tipo, item_id, score, sujetos, calculado_en)"
                + " VALUES ('NACIONAL', '', 'PRODUCTO', ?, ?, 80, ?)",
                producto, score, Timestamp.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearProducto(String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }
}
