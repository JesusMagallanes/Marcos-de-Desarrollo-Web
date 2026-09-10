package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.adaptativo.PesosAdaptativos;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * La cadena que va de «el sistema decidió esto» a «y esto fue lo que pasó».
 *
 * <p>Es lo que faltaba para poder responder si el recomendador mejora. Antes se
 * sabía qué se había enseñado y si se había hecho clic, y con eso no se puede
 * atribuir nada: el módulo no dice qué generador propuso el candidato, no había
 * score, y sobre todo no había forma de saber con qué configuración de pesos se
 * produjo. Dos versiones distintas del ranker quedaban mezcladas en la misma
 * cuenta.
 *
 * <p>NO es transaccional a propósito. El registro escribe en una transacción
 * propia —{@code REQUIRES_NEW}— y desde una prueba con reversión no vería el
 * sujeto, que seguiría sin confirmar. Se limpia a mano, como las demás pruebas
 * de este paquete que tocan varias transacciones.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Medición y atribución")
class MedicionIT extends PruebaIntegracion {

    @Autowired
    private RegistroRecomendacionService registro;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private MetricaDescubrimientoRepository metricas;

    @Autowired
    private MedicionService medicion;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private PesosAdaptativos adaptativos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long categoria;
    private UUID sujeto;
    private LocalDate hoy;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.metrica_descubrimiento");
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.sujeto");
        categoria = crearCategoria("medicion-it-" + UUID.randomUUID());
        sujeto = crearSujeto();
        hoy = LocalDate.now(ZoneOffset.UTC);
    }

    /* ══════════════ Atribución ══════════════ */

    @Test
    @DisplayName("lo servido conserva razón, posición y versión del ranker")
    void seConservaTodoLoQueHaceFaltaParaAtribuir() {
        Long a = crearProducto("Primero");
        Long b = crearProducto("Segundo");

        registro.anotar(sujeto, ModuloDescubrimiento.OTROS_DESCUBRIERON, List.of(a, b),
                Map.of(a, RazonRecomendacion.CO_VIEWED,
                        b, RazonRecomendacion.SIMILAR_SUBJECT),
                Map.of(a, 0.9, b, 0.4), true);

        List<RecomendacionServida> filas = servidas.deSujetoDesde(
                sujeto, Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(filas).hasSize(2);

        RecomendacionServida primera = filas.get(0);
        assertThat(primera.getItemId()).isEqualTo(a);
        assertThat(primera.getPosicion()).as("empieza en 0").isZero();
        assertThat(primera.getRazon())
                .as("la razón por ítem, que es lo que el módulo no puede dar")
                .isEqualTo(RazonRecomendacion.CO_VIEWED);
        assertThat(primera.getScore().doubleValue()).isEqualTo(0.9);
        assertThat(primera.getRankerVersion()).isEqualTo(versionEsperada());

        assertThat(filas.get(1).getRazon())
                .as("el mismo carrusel puede llevar dos razones distintas")
                .isEqualTo(RazonRecomendacion.SIMILAR_SUBJECT);
    }

    @Test
    @DisplayName("cuando el módulo no dice otra cosa, se usa su razón")
    void laRazonPorDefectoEsLaDelModulo() {
        Long a = crearProducto("Popular");

        registro.anotar(sujeto, ModuloDescubrimiento.POPULARES, List.of(a),
                Map.of(), Map.of(), false);

        assertThat(servidas.findAll()).singleElement()
                .satisfies(r -> {
                    assertThat(r.getRazon()).isEqualTo(RazonRecomendacion.POPULAR);
                    assertThat(r.isConPerfil())
                            .as("sin perfil: el arranque en frío se mide aparte")
                            .isFalse();
                });
    }

    @Test
    @DisplayName("la versión cambia sola al mover un peso")
    void laHuellaDelrankerDelataUnCambioDePesos() {
        /*
         * Es la razón de que la versión no sea solo una etiqueta a mano. Una
         * etiqueta se olvida de subir, y entonces dos configuraciones distintas
         * quedan registradas con el mismo nombre: la comparación posterior
         * parece válida y no lo es.
         */
        // Se mira `PesosDescubrimiento` y no la version que se graba: lo que
        // esta a prueba aqui es SU huella, la de la fase 3. Que ademas ahora
        // grabe la del adaptativo es otra propiedad, y tiene su propia prueba.
        String antes = pesos.rankerVersion();
        double original = pesos.getPenalizacionPopularidad();
        try {
            pesos.setPenalizacionPopularidad(original + 0.1);
            assertThat(pesos.rankerVersion())
                    .as("mismo prefijo, huella distinta")
                    .isNotEqualTo(antes)
                    .startsWith(pesos.getRankerEtiqueta() + "-");
        } finally {
            pesos.setPenalizacionPopularidad(original);
        }
        assertThat(pesos.rankerVersion())
                .as("y vuelve a la de antes al restaurar el peso")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("un fallo al anotar no puede tumbar al que llamó")
    void anotarNuncaPropagaSuError() {
        /*
         * La promesa de la clase, comprobada por el camino que de verdad falla:
         * un sujeto que no existe viola la clave ajena. El fallo ocurre al
         * confirmar, que es fuera del cuerpo del método, y por eso el registro
         * tiene que pasar por el proxy para poder capturarlo.
         */
        UUID inexistente = UUID.randomUUID();
        Long a = crearProducto("Da igual");

        registro.anotar(inexistente, ModuloDescubrimiento.POPULARES, List.of(a),
                Map.of(), Map.of(), false);

        assertThat(servidas.findAll()).as("no se escribió nada, y no se rompió nada").isEmpty();
    }


    /**
     * La versión que DEBE quedar grabada: la de la fórmula que ordenó de verdad.
     *
     * <p>No se fija a mano. Si se escribiera «v3.0», la prueba pasaría a estar
     * mintiendo en cuanto se encendiera el ranker adaptativo — que es
     * exactamente lo que pasó — y peor aún: dejaría de comprobar la propiedad
     * que importa, que es que la versión grabada y la fórmula usada no puedan
     * separarse.
     */
    private String versionEsperada() {
        return adaptativos.isActivo() ? adaptativos.version() : pesos.rankerVersion();
    }

    /* ══════════════ Agregación ══════════════ */

    @Test
    @DisplayName("el CTR sale de lo que se vio, no de lo que se sirvió")
    void elCtrSeCalculaSobreLoVisto() {
        Long visto = crearProducto("Visto y pulsado");
        Long noVisto = crearProducto("Servido y nunca alcanzado");

        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);
        anotarEn(ModuloDescubrimiento.POPULARES, List.of(visto, noVisto), ahora);

        // Solo uno llegó a pantalla, y sobre ese se hizo clic.
        impresion(visto, ahora.plus(1, ChronoUnit.MINUTES));
        evento(visto, "ITEM_VIEW", ahora.plus(2, ChronoUnit.MINUTES));

        medicion.agregarDia(hoy);

        List<MetricaDescubrimiento> filas = metricas.entre(hoy, hoy);
        assertThat(filas).isNotEmpty();

        long servidasTotal = filas.stream().mapToLong(MetricaDescubrimiento::getServidas).sum();
        long vistasTotal = filas.stream().mapToLong(MetricaDescubrimiento::getVistas).sum();
        long clicsTotal = filas.stream().mapToLong(MetricaDescubrimiento::getClics).sum();

        assertThat(servidasTotal).isEqualTo(2);
        assertThat(vistasTotal).as("el hueco entre lo propuesto y lo alcanzado es real")
                .isEqualTo(1);
        assertThat(clicsTotal).isEqualTo(1);

        MetricaDescubrimiento conVista = filas.stream()
                .filter(m -> m.getVistas() > 0).findFirst().orElseThrow();
        assertThat(conVista.ctr())
                .as("dividir por lo servido mezclaría calidad con cuánto se desplazó la página")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("las acciones posteriores se atribuyen por tipo")
    void seDistinguenClicCarritoYCompra() {
        Long producto = crearProducto("Comprado");
        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);

        anotarEn(ModuloDescubrimiento.SEGUN_TUS_INTERESES, List.of(producto), ahora);
        impresion(producto, ahora.plus(1, ChronoUnit.MINUTES));
        evento(producto, "ITEM_VIEW_DEEP", ahora.plus(2, ChronoUnit.MINUTES));
        evento(producto, "ADD_TO_CART", ahora.plus(5, ChronoUnit.MINUTES));
        evento(producto, "PURCHASE", ahora.plus(9, ChronoUnit.MINUTES));

        medicion.agregarDia(hoy);

        MetricaDescubrimiento m = metricas.entre(hoy, hoy).get(0);
        assertThat(m.getClics()).isEqualTo(1);
        assertThat(m.getVistasProfundas()).isEqualTo(1);
        assertThat(m.getCarritos()).isEqualTo(1);
        assertThat(m.getCompras())
                .as("un clic no es un éxito; esto sí")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("lo anterior a la recomendación no se le atribuye")
    void noSeAtribuyeHaciaAtras() {
        Long producto = crearProducto("Ya le interesaba");
        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);

        // La compra ocurre ANTES de que el sistema recomendara nada.
        evento(producto, "PURCHASE", ahora.minus(30, ChronoUnit.MINUTES));
        anotarEn(ModuloDescubrimiento.POPULARES, List.of(producto), ahora);
        impresion(producto, ahora.plus(1, ChronoUnit.MINUTES));

        medicion.agregarDia(hoy);

        assertThat(metricas.entre(hoy, hoy).get(0).getCompras())
                .as("atribuirse un mérito anterior es la forma más fácil de mentir")
                .isZero();
    }

    @Test
    @DisplayName("agregar dos veces el mismo día no duplica ni cambia nada")
    void laAgregacionEsIdempotente() {
        Long producto = crearProducto("Cualquiera");
        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);
        anotarEn(ModuloDescubrimiento.POPULARES, List.of(producto), ahora);
        impresion(producto, ahora.plus(1, ChronoUnit.MINUTES));

        medicion.agregarDia(hoy);
        List<MetricaDescubrimiento> primera = metricas.entre(hoy, hoy);

        medicion.agregarDia(hoy);
        List<MetricaDescubrimiento> segunda = metricas.entre(hoy, hoy);

        assertThat(segunda).hasSameSizeAs(primera);
        assertThat(segunda.get(0).getServidas()).isEqualTo(primera.get(0).getServidas());
        assertThat(segunda.get(0).getVistas()).isEqualTo(primera.get(0).getVistas());
    }

    @Test
    @DisplayName("el detalle caduca y el agregado sobrevive")
    void laRetencionBorraElDetalleYConservaLoAgregado() {
        Long producto = crearProducto("Antiguo");
        Instant viejo = Instant.now().minus(pesos.getRetencionDias() + 10L, ChronoUnit.DAYS);

        anotarEn(ModuloDescubrimiento.POPULARES, List.of(producto), viejo);
        LocalDate diaViejo = viejo.atZone(ZoneOffset.UTC).toLocalDate();
        medicion.agregarDia(diaViejo);

        assertThat(servidas.findAll()).isNotEmpty();
        assertThat(metricas.entre(diaViejo, diaViejo)).isNotEmpty();

        medicion.agregarYPurgar();

        assertThat(servidas.findAll())
                .as("el detalle crece con el tráfico y tiene fecha de caducidad")
                .isEmpty();
        assertThat(metricas.entre(diaViejo, diaViejo))
                .as("el agregado es la memoria del sistema: no se purga")
                .isNotEmpty();
    }

    /* ══════════════ Privacidad ══════════════ */

    @Test
    @DisplayName("el agregado no permite llegar a nadie")
    void elAgregadoNoIdentifica() {
        Long producto = crearProducto("Cualquiera");
        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);
        anotarEn(ModuloDescubrimiento.POPULARES, List.of(producto), ahora);
        impresion(producto, ahora.plus(1, ChronoUnit.MINUTES));

        medicion.agregarDia(hoy);
        MetricaDescubrimiento m = metricas.entre(hoy, hoy).get(0);

        /*
         * Se mira la fila entera como texto. Es tosco y por eso atrapa lo que
         * una comprobación campo a campo dejaría pasar el día que alguien añada
         * una columna sin pensarlo.
         */
        assertThat(m.toString()).doesNotContain(sujeto.toString());
        assertThat(m.getSujetos())
                .as("solo el recuento, que sirve justo para lo contrario: descartar"
                        + " filas sostenidas por una sola persona")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("el resumen por razón descarta lo que no sostiene bastante gente")
    void elResumenExigeUnMinimoDeSujetos() {
        Long producto = crearProducto("Cualquiera");
        Instant ahora = Instant.now().minus(2, ChronoUnit.HOURS);
        anotarEn(ModuloDescubrimiento.POPULARES, List.of(producto), ahora);
        medicion.agregarDia(hoy);

        assertThat(metricas.resumenPorRazon(hoy, hoy, 1))
                .as("con el mínimo en 1, la fila entra")
                .isNotEmpty();
        assertThat(metricas.resumenPorRazon(hoy, hoy, pesos.getMinimoSujetos()))
                .as("con 50, una fila de un solo sujeto no puede sostener ninguna conclusión")
                .isEmpty();
    }

    /* ══════════════ Utilidades ══════════════ */

    /**
     * Anota y retrasa la fecha de lo recién escrito.
     *
     * <p>El servicio pone {@code now()} y hace bien: la fecha no es cosa de
     * quien llama. Pero para probar la atribución hacen falta recomendaciones
     * anteriores a las acciones, así que aquí se mueven después, y solo las que
     * acaba de escribir esta llamada.
     */
    private void anotarEn(ModuloDescubrimiento modulo, List<Long> items, Instant cuando) {
        Long ultimoAntes = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM catalogo.recomendacion_servida", Long.class);

        registro.anotar(sujeto, modulo, items, Map.of(), Map.of(), true);

        jdbc.update("UPDATE catalogo.recomendacion_servida SET servido_en = ? WHERE id > ?",
                Timestamp.from(cuando), ultimoAntes);
    }

    private void impresion(Long producto, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.impresion"
                + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                + " VALUES (?, 'PRODUCTO', ?, ?, false, ?)",
                sujeto, producto, moduloDeLaUltimaServida(), Timestamp.from(cuando));
    }

    private String moduloDeLaUltimaServida() {
        return jdbc.queryForObject(
                "SELECT modulo FROM catalogo.recomendacion_servida"
                        + " ORDER BY id DESC LIMIT 1", String.class);
    }

    private void evento(Long producto, String tipo, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, tipo, item_tipo, item_id, ocurrido_en)"
                + " VALUES (?, ?, 'PRODUCTO', ?, ?)",
                sujeto, tipo, producto, Timestamp.from(cuando));
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
