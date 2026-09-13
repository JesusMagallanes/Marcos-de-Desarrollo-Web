package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.backend.catalogo.PruebaIntegracion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.EventoRequest;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteEventosRequest;

/**
 * Lo que alguien ES frente a lo que alguien está HACIENDO ahora.
 *
 * <p>El perfil describe un gusto construido con meses y olvida despacio. Eso es
 * correcto para saber que a alguien le gustan los monitores, y es justo lo que
 * no sirve cuando esa misma persona entra hoy buscando una impresora: el perfil
 * sigue teniendo razón y ha dejado de ayudar.
 *
 * <p>Aquí se comprueba que las dos señales conviven —que ninguna se lleva por
 * delante a la otra— y, sobre todo, que la sesión no es una puerta trasera: un
 * interés intensísimo de esta visita no resucita un descarte ni salta la
 * fatiga.
 *
 * <p>NO es transaccional: el registro escribe en transacción propia.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Temporalidad e intención de sesión")
class SesionIT extends PruebaIntegracion {

    @Autowired
    private SesionService sesiones;

    /*
     * La ingesta de produccion. Las pruebas de FLUJO —las que demuestran que
     * SESSION_INTENT aparece— pasan por aqui, no por INSERT directo: la fase K
     * descubrio que el estado se falsificaba con JDBC y validaba un caso que en
     * produccion no ocurre, porque la ingesta vuelca cada evento al perfil en
     * la misma transaccion. Las pruebas de SERVICIO (ventana, sesion viva,
     * determinismo) siguen con JDBC: ahi el objetivo es SesionService aislado.
     */
    @Autowired
    private IngestaService ingesta;

    @Autowired
    private RecomendacionService recomendador;

    @Autowired
    private RecomendacionServidaRepository servidas;

    @Autowired
    private PerfilFacetaRepository perfiles;

    @Autowired
    private PesosDescubrimiento pesos;

    @Autowired
    private JdbcTemplate jdbc;

    private Long catMonitores;
    private Long catImpresoras;
    private UUID sujeto;
    private UUID sesion;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida");
        jdbc.update("DELETE FROM catalogo.item_descartado");
        jdbc.update("DELETE FROM catalogo.impresion");
        jdbc.update("DELETE FROM catalogo.evento_interaccion");
        jdbc.update("DELETE FROM catalogo.perfil_faceta");
        jdbc.update("DELETE FROM catalogo.sujeto");
        catMonitores = crearCategoria("sesion-monitores-" + UUID.randomUUID());
        catImpresoras = crearCategoria("sesion-impresoras-" + UUID.randomUUID());
        sujeto = crearSujeto();
        sesion = UUID.randomUUID();
    }

    @AfterEach
    void devolverElCatalogoComoEstaba() {
        for (Long c : List.of(catMonitores, catImpresoras)) {
            jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE item_id IN"
                    + " (SELECT id FROM catalogo.producto WHERE categoria_id = ?)", c);
            jdbc.update("DELETE FROM catalogo.producto WHERE categoria_id = ?", c);
            jdbc.update("DELETE FROM catalogo.categoria WHERE id = ?", c);
        }
    }

    /* ══════════════ La sesión se deduce ══════════════ */

    @Test
    @DisplayName("la sesión activa se deduce del último evento")
    void laSesionSeDeduce() {
        /*
         * El identificador de sesión lo genera el navegador y solo viaja en la
         * ingesta: las peticiones de lectura no lo llevan. Deducirlo evita
         * cambiar el contrato y hace imposible que alguien mande la sesión de
         * otro.
         */
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        SesionService.Intencion intencion = sesiones.intencionDe(sujeto);

        assertThat(intencion.hayIntencion()).isTrue();
        assertThat(intencion.categoriasDeSesion(3)).containsExactly(catMonitores);
    }

    @Test
    @DisplayName("una sesión vieja ya no está viva")
    void laSesionCaduca() {
        Instant vieja = Instant.now()
                .minus(pesos.getSesionVentanaMinutos() + 15L, ChronoUnit.MINUTES);
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), vieja);
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), vieja);

        assertThat(sesiones.intencionDe(sujeto).hayIntencion())
                .as("media hora sin tocar nada: esa visita terminó")
                .isFalse();
    }

    @Test
    @DisplayName("otra sesión no mezcla su intención")
    void dosSesionesNoSeMezclan() {
        UUID otraSesion = UUID.randomUUID();
        // La visita ANTERIOR fue de impresoras; la de ahora, de monitores.
        ver(sujeto, otraSesion, crearProducto(catImpresoras, "Impresora"), hace(20));
        ver(sujeto, otraSesion, crearProducto(catImpresoras, "Impresora 2"), hace(19));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        assertThat(sesiones.intencionDe(sujeto).categoriasDeSesion(5))
                .as("la intención es de la visita en curso, no de la anterior")
                .containsExactly(catMonitores);
    }

    @Test
    @DisplayName("una sola interacción no es una intención")
    void unClicSueltoNoPersonaliza() {
        /*
         * Dejar que mande convertiría el Home en un monográfico por un
         * resbalón. Sin señal suficiente, el sistema hace lo que hacía.
         */
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(1));

        assertThat(sesiones.intencionDe(sujeto).hayIntencion()).isFalse();
    }

    @Test
    @DisplayName("sin actividad no hay intención que deducir")
    void sinActividadNoHayIntencion() {
        assertThat(sesiones.intencionDe(sujeto).hayIntencion()).isFalse();
        assertThat(sesiones.intencionDe(null).hayIntencion()).isFalse();
    }

    /* ══════════════ Repetición y saturación ══════════════ */

    @Test
    @DisplayName("cien recargas no son cien intereses")
    void laRepeticionNoInflaIndefinidamente() {
        /*
         * Es el accidente más común —una pestaña que se recarga— y el fraude
         * más barato. Con el factor 1/(1+ln n), el segundo evento igual vale
         * 0,59 y el décimo 0,30: sigue contando más que uno aislado, pero deja
         * de crecer casi enseguida.
         */
        Long monitor = crearProducto(catMonitores, "Monitor insistente");
        Long impresora1 = crearProducto(catImpresoras, "Impresora A");
        Long impresora2 = crearProducto(catImpresoras, "Impresora B");

        for (int i = 0; i < 100; i++) {
            ver(sujeto, sesion, monitor, hace(1));
        }
        ver(sujeto, sesion, impresora1, hace(1));
        ver(sujeto, sesion, impresora2, hace(1));

        List<SesionService.Intencion.Faceta> facetas = sesiones.intencionDe(sujeto).categorias();

        double monitores = pesoDe(facetas, catMonitores);
        double impresoras = pesoDe(facetas, catImpresoras);

        assertThat(monitores)
                .as("cien vistas del mismo producto no pueden valer cien")
                .isLessThan(impresoras * 3);
    }

    /* ══════════════ Que no sea una puerta trasera ══════════════ */

    @Test
    @DisplayName("«no me interesa» gana a la intención de sesión")
    void elDescarteGanaALaSesion() {
        /*
         * La jerarquía que exige el encargo:
         *   NOT_INTERESTED > sesión > reciente > histórico
         * Ninguna señal temporal puede devolver algo excluido.
         */
        Long descartado = crearProducto(catMonitores, "Monitor descartado");
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        jdbc.update("INSERT INTO catalogo.item_descartado"
                + " (sujeto_id, item_tipo, item_id, motivo) VALUES (?, 'PRODUCTO', ?, ?)",
                sujeto, descartado, "NOT_INTERESTED");

        Carrusel carrusel = recomendador.segunIntereses(sujeto, 12, new LinkedHashSet<>());

        assertThat(idsDe(carrusel))
                .as("está en la categoría que mira ahora mismo, y aun así no vuelve")
                .doesNotContain(descartado);
        assertThat(servidas.findAll()).extracting(RecomendacionServida::getItemId)
                .as("ni llegó a ser candidato")
                .doesNotContain(descartado);
    }

    @Test
    @DisplayName("la fatiga tampoco se salta por interés de sesión")
    void laFatigaGanaALaSesion() {
        Long fatigado = crearProducto(catMonitores, "Monitor ya muy visto");
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        for (int i = 0; i < pesos.getCooldownMaximo(); i++) {
            jdbc.update("INSERT INTO catalogo.impresion"
                    + " (sujeto_id, item_tipo, item_id, modulo, con_clic, mostrado_en)"
                    + " VALUES (?, 'PRODUCTO', ?, 'SEGUN_TUS_INTERESES', false, ?)",
                    sujeto, fatigado, Timestamp.from(hace(60)));
        }

        assertThat(idsDe(recomendador.segunIntereses(sujeto, 12, new LinkedHashSet<>())))
                .as("se le enseñó de sobra y lo ignoró, mire lo que mire ahora")
                .doesNotContain(fatigado);
    }

    @Test
    @DisplayName("«no me interesa» nunca se convierte en interés de sesión")
    void elDescarteNoAlimentaLaIntencion() {
        Long producto = crearProducto(catImpresoras, "Impresora descartada");
        eventoDe(sujeto, sesion, producto, catImpresoras, "NOT_INTERESTED", hace(1));
        eventoDe(sujeto, sesion, producto, catImpresoras, "DISMISS", hace(1));

        assertThat(sesiones.intencionDe(sujeto).hayIntencion())
                .as("rechazar algo dos veces no es explorar esa categoría")
                .isFalse();
    }

    /* ══════════════ Arranque en frío ══════════════ */

    @Test
    @DisplayName("la sesión real produce SESSION_INTENT desde el flujo de ingesta")
    void laSesionRealProduceSessionIntent() {
        /*
         * EL caso de la fase K, y por el camino de produccion.
         *
         * Dos vistas de monitores entran por la ingesta REAL: se persisten y,
         * en la MISMA transaccion, actualizan el perfil. Es justo el flujo que
         * hacia que SESSION_INTENT no apareciera —el perfil ya trae la
         * categoria cuando se sirve el Home—. Con la atribucion de K, esos
         * candidatos se anotan como SESSION_INTENT porque su categoria es la de
         * la sesion viva. No se falsifica ningun `perfil_faceta`.
         */
        crearProducto(catMonitores, "Monitor candidato A");
        crearProducto(catMonitores, "Monitor candidato B");
        verReal(crearProducto(catMonitores, "Monitor visto 1"), catMonitores, sesion);
        verReal(crearProducto(catMonitores, "Monitor visto 2"), catMonitores, sesion);

        assertThat(perfiles.todasDe(sujeto))
                .as("la ingesta real SI crea perfil: es la premisa de K, no un fallo")
                .isNotEmpty();

        List<Carrusel> home = recomendador.home(sujeto, null, 12);

        assertThat(home).anySatisfy(c ->
                assertThat(c.modulo()).isEqualTo(ModuloDescubrimiento.SEGUN_TUS_INTERESES.name()));
        assertThat(servidas.findAll()).extracting(RecomendacionServida::getRazon)
                .as("y desde el flujo real aparece la atribución de sesión")
                .contains(RazonRecomendacion.SESSION_INTENT);

        List<Long> servidos = servidas.findAll().stream()
                .map(RecomendacionServida::getItemId).toList();
        assertThat(servidos)
                .as("ningún producto se sirve dos veces")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("sin perfil y sin sesión, el Home es el de siempre")
    void sinPerfilNiSesionNoSePersonaliza() {
        List<Carrusel> home = recomendador.home(sujeto, null, 12);

        assertThat(home).noneSatisfy(c ->
                assertThat(c.modulo()).isEqualTo(ModuloDescubrimiento.SEGUN_TUS_INTERESES.name()));
        assertThat(home).as("sigue habiendo carruseles: tendencia y populares").isNotEmpty();
    }

    @Test
    @DisplayName("el arranque en frío se sigue midiendo por el PERFIL, no por la sesión")
    void laSesionNoCuentaComoPerfil() {
        /*
         * Con sesión se personaliza, pero eso no es tener perfil. Anotarlo como
         * si lo fuera volvería a vaciar de sentido la segmentación de arranque
         * en frío, que es la que dice si el recomendador sirve a quien llega.
         */
        crearProducto(catMonitores, "Monitor candidato");
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor 1"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor 2"), hace(1));

        recomendador.home(sujeto, null, 12);

        assertThat(servidas.findAll())
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.isConPerfil())
                        .as("hay sesión, pero perfil no hay")
                        .isFalse());
    }

    /* ══════════════ Sesión y perfil conviven ══════════════ */

    @Test
    @DisplayName("perfil e intención conviven, cada uno con su razón, sin estado falso")
    void lasDosSenalesConviven() {
        /*
         * El caso mixto de K, construido SOLO con ingesta real.
         *
         * Primero una sesion de impresoras; despues, mas tarde, una de
         * monitores. Las dos crean perfil —es lo que hace la ingesta— pero la
         * sesion VIVA es la ultima, la de monitores. Asi, sin falsificar nada:
         *
         *   monitores  → perfil + sesion viva → SESSION_INTENT
         *   impresoras → perfil, fuera de la sesion viva → PERSONAL_INTEREST
         *
         * El caso «solo sesion, sin perfil» no se construye porque en
         * produccion no existe: la ingesta siempre escribe perfil. Ese es
         * justamente el hallazgo de K, no un hueco de la prueba.
         */
        for (int i = 0; i < 4; i++) {
            crearProducto(catMonitores, "Monitor catalogo " + i);
            crearProducto(catImpresoras, "Impresora catalogo " + i);
        }

        UUID sesionVieja = UUID.randomUUID();
        verReal(crearProducto(catImpresoras, "Impresora vista 1"), catImpresoras, sesionVieja);
        verReal(crearProducto(catImpresoras, "Impresora vista 2"), catImpresoras, sesionVieja);
        esperarUnInstante();
        verReal(crearProducto(catMonitores, "Monitor visto 1"), catMonitores, sesion);
        verReal(crearProducto(catMonitores, "Monitor visto 2"), catMonitores, sesion);

        recomendador.segunIntereses(sujeto, 12, new LinkedHashSet<>());

        List<RecomendacionServida> servido = servidas.findAll();
        assertThat(servido.stream().map(RecomendacionServida::getItemId).toList())
                .as("ningún producto se sirve dos veces")
                .doesNotHaveDuplicates();

        Long catDe = null;
        for (RecomendacionServida r : servido) {
            Long cat = jdbc.queryForObject(
                    "SELECT categoria_id FROM catalogo.producto WHERE id = ?",
                    Long.class, r.getItemId());
            if (cat.equals(catMonitores)) {
                assertThat(r.getRazon())
                        .as("lo de la sesión viva es intención de sesión")
                        .isEqualTo(RazonRecomendacion.SESSION_INTENT);
            } else if (cat.equals(catImpresoras)) {
                assertThat(r.getRazon())
                        .as("lo del perfil fuera de la sesión es interés histórico")
                        .isEqualTo(RazonRecomendacion.PERSONAL_INTEREST);
                catDe = cat;
            }
        }
        assertThat(servido).extracting(RecomendacionServida::getRazon)
                .contains(RazonRecomendacion.SESSION_INTENT)
                .contains(RazonRecomendacion.PERSONAL_INTEREST);
        assertThat(catDe).as("hubo al menos una impresora, para el contraste").isNotNull();
    }

    /* ══════════════ Identidad ══════════════ */

    @Test
    @DisplayName("un sujeto nuevo no hereda la sesión del anterior")
    void noSeHeredaLaIntencionAlCambiarDeSujeto() {
        /*
         * La sesión es contexto de navegación; la identidad es otra cosa. Al
         * cerrar sesión el navegador estrena sujeto, y aunque el identificador
         * técnico de sesión fuera el mismo, la intención se deduce POR SUJETO.
         */
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        UUID siguienteVisitante = crearSujeto();

        assertThat(sesiones.intencionDe(siguienteVisitante).hayIntencion())
                .as("misma sesión técnica, otra persona: no hereda nada")
                .isFalse();
        assertThat(sesiones.intencionDe(sujeto).hayIntencion()).isTrue();
    }

    /* ══════════════ Determinismo ══════════════ */

    @Test
    @DisplayName("los mismos datos producen la misma intención")
    void esDeterminista() {
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), hace(2));
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), hace(1));

        assertThat(sesiones.intencionDe(sujeto).categoriasDeSesion(5))
                .isEqualTo(sesiones.intencionDe(sujeto).categoriasDeSesion(5));
    }

    @Test
    @DisplayName("un evento con fecha futura no construye interés")
    void elFuturoNoCuenta() {
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor A"), manana);
        ver(sujeto, sesion, crearProducto(catMonitores, "Monitor B"), manana);

        assertThat(sesiones.intencionDe(sujeto).hayIntencion())
                .as("un reloj adelantado en un cliente no puede fabricar interés")
                .isFalse();
    }

    /* ══════════════ K · la atribución no mueve el ranking ══════════════ */

    @Test
    @DisplayName("cambiar la razón a SESSION_INTENT no cambia ítem, posición, score ni módulo")
    void laAtribucionNoTocaElRanking() {
        /*
         * La garantia central de la alternativa A: la razon es lo UNICO que
         * puede cambiar. Se comparan dos sujetos con el MISMO perfil y el mismo
         * catalogo; uno tiene una sesion viva en esa categoria y el otro no.
         *
         * El de la sesion vera SESSION_INTENT; el otro, PERSONAL_INTEREST. Todo
         * lo demas —que producto, en que puesto, con que score, en que modulo—
         * tiene que coincidir producto a producto. Si algo de eso se mueve, la
         * atribucion estaria tocando el ranking y A no se sostendria.
         *
         * La sesion del primer sujeto se siembra por JDBC A PROPOSITO: aqui el
         * objetivo es aislar el efecto de la atribucion, y la ingesta real
         * cambiaria tambien su perfil, con lo que los dos sujetos dejarian de
         * ser comparables. El flujo real ya se prueba en las otras dos.
         */
        String slug = jdbc.queryForObject(
                "SELECT slug FROM catalogo.categoria WHERE id = ?", String.class, catMonitores);

        // Cuatro productos con marcas de peso distinto: orden total, sin empates.
        List<Long> productos = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long marca = crearMarca("K-marca-" + i);
            productos.add(crearProductoConMarca(catMonitores, "K monitor " + i, marca));
        }

        UUID conSesion = crearSujeto();
        UUID sinSesion = crearSujeto();
        for (UUID quien : List.of(conSesion, sinSesion)) {
            sembrarPerfilCategoria(quien, slug, 50.0, 20);
            int score = 40;
            for (Long producto : productos) {
                sembrarPerfilMarca(quien, marcaDe(producto), score);
                score -= 10;
            }
        }
        // Solo el primero tiene una visita viva en la categoria del perfil.
        for (Long producto : productos) {
            ver(conSesion, sesion, producto, hace(1));
        }

        Map<Long, RecomendacionServida> conRazonSesion = servir(conSesion);
        Map<Long, RecomendacionServida> conRazonPerfil = servir(sinSesion);

        assertThat(conRazonSesion.keySet())
                .as("los dos sirven exactamente los mismos productos")
                .isEqualTo(conRazonPerfil.keySet());

        for (Long item : conRazonSesion.keySet()) {
            RecomendacionServida a = conRazonSesion.get(item);
            RecomendacionServida b = conRazonPerfil.get(item);
            assertThat(a.getPosicion()).as("misma posición para " + item)
                    .isEqualTo(b.getPosicion());
            assertThat(a.getScore()).as("mismo score para " + item)
                    .isEqualByComparingTo(b.getScore());
            assertThat(a.getModulo()).as("mismo módulo para " + item)
                    .isEqualTo(b.getModulo());
        }

        assertThat(conRazonSesion.values()).extracting(RecomendacionServida::getRazon)
                .as("con sesión viva, la atribución es de sesión")
                .contains(RazonRecomendacion.SESSION_INTENT);
        assertThat(conRazonPerfil.values()).extracting(RecomendacionServida::getRazon)
                .as("sin sesión, todo es interés histórico y nada se cuela como sesión")
                .containsOnly(RazonRecomendacion.PERSONAL_INTEREST);
    }

    /** Sirve el módulo personal a un sujeto y devuelve lo anotado, por ítem. */
    private Map<Long, RecomendacionServida> servir(UUID quien) {
        jdbc.update("DELETE FROM catalogo.recomendacion_servida WHERE sujeto_id = ?", quien);
        recomendador.segunIntereses(quien, 12, new LinkedHashSet<>());
        Map<Long, RecomendacionServida> porItem = new HashMap<>();
        for (RecomendacionServida r : servidas.findAll()) {
            if (r.getSujetoId().equals(quien)) {
                porItem.put(r.getItemId(), r);
            }
        }
        return porItem;
    }

    /* ══════════════ Utilidades ══════════════ */

    private double pesoDe(List<SesionService.Intencion.Faceta> facetas, Long categoria) {
        return facetas.stream()
                .filter(f -> f.categoriaId().equals(categoria))
                .mapToDouble(SesionService.Intencion.Faceta::sesion)
                .sum();
    }

    private List<Long> idsDe(Carrusel carrusel) {
        return carrusel == null ? List.of() : carrusel.items().stream().map(p -> p.id()).toList();
    }

    private Instant hace(int minutos) {
        return Instant.now().minus(minutos, ChronoUnit.MINUTES);
    }

    private Long crearCategoria(String slug) {
        jdbc.update("INSERT INTO catalogo.categoria (name, slug, description)"
                + " VALUES (?, ?, 'IT')", slug, slug);
        return jdbc.queryForObject("SELECT id FROM catalogo.categoria WHERE slug = ?",
                Long.class, slug);
    }

    private Long crearProducto(Long categoria, String nombre) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private Long crearMarca(String base) {
        String nombre = base + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.marca (name, descripcion) VALUES (?, 'IT')", nombre);
        return jdbc.queryForObject("SELECT id FROM catalogo.marca WHERE name = ?",
                Long.class, nombre);
    }

    private Long crearProductoConMarca(Long categoria, String nombre, Long marca) {
        String unico = nombre + " " + UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.producto"
                + " (name, description, precio, stock, categoria_id, marca_id, estado_moderacion)"
                + " VALUES (?, 'IT', ?, 10, ?, ?, 'APROBADO')",
                unico, new BigDecimal("100.00"), categoria, marca);
        return jdbc.queryForObject("SELECT id FROM catalogo.producto WHERE name = ?",
                Long.class, unico);
    }

    private String marcaDe(Long producto) {
        return jdbc.queryForObject("SELECT m.name FROM catalogo.marca m"
                + " JOIN catalogo.producto p ON p.marca_id = m.id WHERE p.id = ?",
                String.class, producto);
    }

    private void sembrarPerfilCategoria(UUID quien, String slug, double score, int eventos) {
        jdbc.update("INSERT INTO catalogo.perfil_faceta"
                + " (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)"
                + " VALUES (?, 'CATEGORIA', ?, ?, ?, now())", quien, slug, score, eventos);
    }

    private void sembrarPerfilMarca(UUID quien, String marca, double score) {
        jdbc.update("INSERT INTO catalogo.perfil_faceta"
                + " (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)"
                + " VALUES (?, 'MARCA', ?, ?, 15, now())", quien, marca, score);
    }

    private UUID crearSujeto() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo.sujeto (id) VALUES (?)", id);
        return id;
    }

    private void ver(UUID quien, UUID cualSesion, Long producto, Instant cuando) {
        eventoDe(quien, cualSesion, producto, null, "ITEM_VIEW", cuando);
    }

    /**
     * Una vista por el flujo REAL de ingesta.
     *
     * <p>Persiste el evento y actualiza el perfil en la misma transaccion,
     * exactamente como en produccion, con {@code ocurrido_en = now()}. Es el
     * camino que las pruebas de flujo de K tienen que ejercer.
     */
    private void verReal(Long producto, Long categoria, UUID cualSesion) {
        EventoRequest evento = new EventoRequest(
                TipoEvento.ITEM_VIEW_DEEP, TipoItem.PRODUCTO, producto, categoria,
                5000, null, null, null, null, null);
        ingesta.registrar(sujeto,
                new LoteEventosRequest(null, null, cualSesion, null, List.of(evento)));
    }

    /** Un respiro real para que dos sesiones no empaten en `ocurrido_en`. */
    private void esperarUnInstante() {
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void eventoDe(UUID quien, UUID cualSesion, Long producto, Long categoria,
            String tipo, Instant cuando) {
        jdbc.update("INSERT INTO catalogo.evento_interaccion"
                + " (sujeto_id, sesion_id, tipo, item_tipo, item_id, categoria_id, ocurrido_en)"
                + " VALUES (?, ?, ?, 'PRODUCTO', ?, ?, ?)",
                quien, cualSesion, tipo, producto, categoria, Timestamp.from(cuando));
    }
}
