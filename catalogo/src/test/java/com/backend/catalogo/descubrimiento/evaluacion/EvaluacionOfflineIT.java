package com.backend.catalogo.descubrimiento.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * La evaluación offline, y sobre todo lo que NO puede hacer.
 *
 * <p>Una evaluación con fuga temporal no falla: sale bien. Enseña unas cifras
 * estupendas y nadie sospecha, porque para verla hay que buscarla a propósito.
 * De ahí que la mitad de esta clase sean pruebas de que el corte se respeta:
 * son las únicas que separan una medida de un autoengaño.
 *
 * <p>Los eventos se fabrican con fecha explícita a los dos lados del corte, que
 * es la única forma de comprobar que la frontera existe.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Evaluación offline")
@Transactional
class EvaluacionOfflineIT extends PruebaIntegracion {

    private static final Duration ENTRENAMIENTO = Duration.ofDays(30);
    private static final Duration HOLDOUT = Duration.ofDays(7);

    @Autowired
    private EvaluacionOfflineService evaluador;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private Instant corte;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("evaluacion-it-" + UUID.randomUUID());
        corte = Instant.now().minus(3, ChronoUnit.DAYS);
    }

    /* ══════════════ Que la frontera exista ══════════════ */

    @Test
    @DisplayName("lo que pasó DESPUÉS del corte no construye la recomendación")
    void noSeFiltraElFuturoHaciaLaConstruccion() {
        /*
         * El montaje es el mínimo que delata una fuga. El sujeto no hace
         * absolutamente nada antes del corte; toda su actividad —y la de los
         * demás— es posterior. Un evaluador que leyera `perfil_faceta` o
         * `item_relacion`, que ya contienen ese futuro, produciría una lista y
         * acertaría. Uno honesto no tiene nada con que construir.
         */
        /*
         * En otra categoria a proposito. Si estuviera en la misma que lo que el
         * sujeto miro, la afinidad personal lo alcanzaria con datos legitimos
         * anteriores al corte y la prueba no distinguiria una fuga de un
         * acierto normal.
         */
        Long producto = crearProductoAjeno("Solo despues");
        UUID sujeto = crearSujeto();

        // Historia mínima ANTES, en otra categoría, para que el sujeto exista.
        Long otro = crearProducto("Antes");
        ver(sujeto, otro, corte.minus(10, ChronoUnit.DAYS));

        // Todo el patrón que "predeciría" el acierto ocurre DESPUÉS.
        for (int i = 0; i < 10; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, otro, corte.plus(1, ChronoUnit.HOURS));
            ver(vecino, producto, corte.plus(1, ChronoUnit.HOURS));
        }
        ver(sujeto, producto, corte.plus(2, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.recall10())
                .as("el patrón que llevaría a ese producto todavía no existía en el corte")
                .isZero();
    }

    @Test
    @DisplayName("lo anterior al corte sí construye, y entonces acierta")
    void conEvidenciaAnteriorSiAcierta() {
        /*
         * El reverso de la prueba anterior, y hace falta: sin ella, un evaluador
         * roto que nunca recomendara nada pasaría la de arriba con matrícula.
         */
        Long semilla = crearProducto("Semilla");
        Long destino = crearProducto("Destino");
        UUID sujeto = crearSujeto();

        for (int i = 0; i < 6; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, corte.minus(5, ChronoUnit.DAYS));
            ver(vecino, destino, corte.minus(5, ChronoUnit.DAYS));
        }
        ver(sujeto, semilla, corte.minus(4, ChronoUnit.DAYS));

        // Y despues hace lo que el patron predecia.
        ver(sujeto, destino, corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.sujetosEvaluados()).isPositive();
        assertThat(r.recall10())
                .as("la evidencia estaba antes del corte: esto sí debe acertarse")
                .isPositive();
    }

    @Test
    @DisplayName("lo anterior a la ventana de entrenamiento tampoco cuenta")
    void laVentanaDeEntrenamientoTambienAcota() {
        Long semilla = crearProducto("Semilla vieja");
        Long destino = crearProducto("Destino viejo");
        UUID sujeto = crearSujeto();

        // Justo por delante del borde: hace mas de treinta dias del corte.
        Instant demasiadoViejo = corte.minus(ENTRENAMIENTO).minus(5, ChronoUnit.DAYS);
        for (int i = 0; i < 6; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, demasiadoViejo);
            ver(vecino, destino, demasiadoViejo);
        }
        ver(sujeto, semilla, demasiadoViejo);
        ver(sujeto, destino, corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.recall10())
                .as("el corte tiene dos bordes, y el de atrás también se respeta")
                .isZero();
    }

    @Test
    @DisplayName("lo posterior al holdout no cuenta como acierto")
    void elHoldoutTieneFinal() {
        Long semilla = crearProducto("Semilla");
        Long destino = crearProducto("Destino");
        UUID sujeto = crearSujeto();

        for (int i = 0; i < 6; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, corte.minus(5, ChronoUnit.DAYS));
            ver(vecino, destino, corte.minus(5, ChronoUnit.DAYS));
        }
        ver(sujeto, semilla, corte.minus(4, ChronoUnit.DAYS));

        // Compra, pero un mes despues: eso ya no lo explica esta recomendacion.
        ver(sujeto, destino, corte.plus(HOLDOUT).plus(20, ChronoUnit.DAYS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.sujetosEvaluados())
                .as("sin nada que comprobar dentro del holdout, no hay sujeto que evaluar")
                .isZero();
    }

    /* ══════════════ Que las métricas midan ══════════════ */

    @Test
    @DisplayName("no se recomienda lo que el sujeto ya había tocado")
    void laRepeticionEsCero() {
        UUID sujeto = crearSujeto();
        Long visto = crearProducto("Ya visto");
        crearProducto("Otro de la categoria");
        crearProducto("Y otro mas");

        ver(sujeto, visto, corte.minus(2, ChronoUnit.DAYS));
        ver(sujeto, crearProducto("Cualquiera"), corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.repeticion())
                .as("medido, no supuesto: si alguien rompe la exclusión, esto sube")
                .isZero();
    }

    @Test
    @DisplayName("la cobertura no puede pasar del catálogo elegible")
    void laCoberturaEstaAcotada() {
        UUID sujeto = crearSujeto();
        for (int i = 0; i < 5; i++) {
            crearProducto("Producto " + i);
        }
        Long semilla = crearProducto("Semilla");
        ver(sujeto, semilla, corte.minus(2, ChronoUnit.DAYS));
        ver(sujeto, crearProducto("Futuro"), corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.cobertura()).isBetween(0.0, 1.0);
        assertThat(r.diversidadCategoria()).isBetween(0.0, 1.0);
        assertThat(r.novedad()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("el arranque en frío se mide aparte y no se mezcla")
    void elArranqueEnFrioSeSepara() {
        // Uno con historia de sobra, otro con casi nada.
        UUID veterano = crearSujeto();
        UUID recien = crearSujeto();
        Long semilla = crearProducto("Semilla");

        for (int i = 0; i < 12; i++) {
            ver(veterano, crearProducto("Visto " + i), corte.minus(5, ChronoUnit.DAYS));
        }
        ver(recien, semilla, corte.minus(1, ChronoUnit.DAYS));

        ver(veterano, crearProducto("Futuro A"), corte.plus(1, ChronoUnit.HOURS));
        ver(recien, crearProducto("Futuro B"), corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.recallPorHistorial())
                .as("mezclarlos escondería el peor problema posible: que solo"
                        + " funcione para quien ya lo usa")
                .containsKeys(ResultadoEvaluacion.Historial.ESCASO,
                        ResultadoEvaluacion.Historial.SUFICIENTE);
    }

    @Test
    @DisplayName("un superventas no se lleva todas las recomendaciones")
    void elSuperventasNoColapsaElSistema() {
        /*
         * El colapso por popularidad es el final natural de un recomendador al
         * que nadie vigila: lo popular se recomienda, al recomendarse se ve, al
         * verse sube, y a las pocas semanas media tienda es invisible. No porque
         * sea peor, sino porque nunca le tocó salir.
         *
         * Aquí se monta el escenario extremo a propósito: uno que ha visto
         * TODO el mundo y varios de nicho. Se mide lo que de verdad importa —
         * cuánto del catálogo llega a asomar y cuánto se aleja la lista de lo
         * más visto—, no si el superventas aparece: aparecer es legítimo,
         * copar no.
         */
        Long superventas = crearProducto("Superventas");
        for (int i = 0; i < 60; i++) {
            ver(crearSujeto(), superventas, corte.minus(5, ChronoUnit.DAYS));
        }

        List<Long> nicho = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Long p = crearProducto("Nicho " + i);
            nicho.add(p);
            for (int v = 0; v < 3; v++) {
                ver(crearSujeto(), p, corte.minus(5, ChronoUnit.DAYS));
            }
        }

        // Veinte visitantes con algo de historia, y actividad en el holdout.
        for (int i = 0; i < 20; i++) {
            UUID s = crearSujeto();
            ver(s, nicho.get(i % nicho.size()), corte.minus(2, ChronoUnit.DAYS));
            ver(s, crearProducto("Futuro " + i), corte.plus(1, ChronoUnit.HOURS));
        }

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.sujetosEvaluados()).isPositive();
        assertThat(r.cobertura())
                .as("si un solo producto copara las listas, esto sería casi cero")
                .isGreaterThan(0.10);
        assertThat(r.novedad())
                .as("las listas no pueden ser el superventas repetido")
                .isGreaterThan(0.30);
    }

    /* ══════════════ Que comparar sirva ══════════════ */

    @Test
    @DisplayName("dos configuraciones distintas producen resultados distintos")
    void losPesosCambianElResultado() {
        /*
         * Si mover los pesos no cambiara nada, toda la fase 3 sería teatro: se
         * podría «optimizar» durante meses sin que el sistema se enterara.
         */
        Long semilla = crearProducto("Semilla");
        /*
         * Fuera de la categoria del sujeto, para que la afinidad personal no lo
         * alcance; y ADEMAS menos popular que el ruido de abajo, para que
         * tampoco lo alcance la tendencia. Asi la unica via que queda es la
         * colaborativa, que es la que la segunda configuracion apaga.
         *
         * Sin esa segunda precaucion la prueba pasaba por el motivo equivocado:
         * los seis vecinos que crean la co-visita lo hacen tambien popular, y
         * salia igual con la senal colaborativa a cero.
         */
        Long porColaboracion = crearProductoAjeno("Llega por colaboracion");
        UUID sujeto = crearSujeto();

        for (int i = 0; i < 6; i++) {
            UUID vecino = crearSujeto();
            ver(vecino, semilla, corte.minus(5, ChronoUnit.DAYS));
            ver(vecino, porColaboracion, corte.minus(5, ChronoUnit.DAYS));
        }

        /*
         * Ruido mas visto que el objetivo: doce productos con doce visitantes
         * cada uno, que es lo que llena el top 10 cuando no hay colaborativo.
         *
         * Cada uno en su propia categoria, y esto tambien costo una vuelta: al
         * crearlos en la del sujeto, la afinidad personal los recomendaba a
         * todos y el top 10 se llenaba de ruido incluso CON la senal
         * colaborativa encendida. El ruido tiene que aportar popularidad y nada
         * mas.
         */
        for (int p = 0; p < 12; p++) {
            Long ruido = crearProductoAjeno("Ruido " + p);
            for (int v = 0; v < 12; v++) {
                ver(crearSujeto(), ruido, corte.minus(5, ChronoUnit.DAYS));
            }
        }

        ver(sujeto, semilla, corte.minus(4, ChronoUnit.DAYS));
        ver(sujeto, porColaboracion, corte.plus(1, ChronoUnit.HOURS));

        ConfiguracionRanker sinColaborativo = base().con("sin-cohorte", Origen.COHORTE, 0.0);

        List<ResultadoEvaluacion> resultados = evaluador.comparar(
                List.of(base(), sinColaborativo), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(resultados).hasSize(2);
        assertThat(resultados.get(0).ndcg10())
                .as("apagar la señal que sostenía el acierto tiene que notarse")
                .isGreaterThan(resultados.get(1).ndcg10());
    }

    @Test
    @DisplayName("el índice global no esconde los objetivos por separado")
    void elIndiceGlobalConvive() {
        UUID sujeto = crearSujeto();
        ver(sujeto, crearProducto("Semilla"), corte.minus(2, ChronoUnit.DAYS));
        ver(sujeto, crearProducto("Futuro"), corte.plus(1, ChronoUnit.HOURS));

        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        // El global existe para ordenar candidatas, no para sustituir a nada.
        assertThat(r.indiceGlobal()).isBetween(0.0, 1.0);
        assertThat(r.ndcg10()).isBetween(0.0, 1.0);
        assertThat(r.cobertura()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("sin datos no inventa un resultado")
    void sinDatosDevuelveVacio() {
        ResultadoEvaluacion r = evaluador.evaluar(base(), corte, ENTRENAMIENTO, HOLDOUT);

        assertThat(r.sujetosEvaluados()).isZero();
        assertThat(r.recall10()).isZero();
    }

    /* ══════════════ Utilidades ══════════════ */

    private ConfiguracionRanker base() {
        return ConfiguracionRanker.enProduccion(pesos);
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    /** En OTRA categoría, para que la afinidad personal no pueda alcanzarlo. */
    private Long crearProductoAjeno(String nombre) {
        Long otra = crearCategoria("ajena-" + UUID.randomUUID());
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), otra);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
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

    private void ver(UUID sujeto, Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, 'ITEM_VIEW', 'PRODUCTO', ?, ?)",
                sujeto, producto, Timestamp.from(cuando));
    }
}
