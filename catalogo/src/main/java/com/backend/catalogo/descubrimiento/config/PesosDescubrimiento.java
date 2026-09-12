package com.backend.catalogo.descubrimiento.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.TipoEvento;

import lombok.Getter;
import lombok.Setter;

/**
 * Toda la calibración del recomendador, en un solo sitio.
 *
 * <p>Los pesos no son constantes del código a propósito. Son lo primero que
 * hay que ajustar cuando empiezan a llegar datos reales, y tener que
 * recompilar y desplegar para mover un número mata la capacidad de aprender.
 * Se sobrescriben desde {@code application.properties} con el prefijo
 * {@code descubrimiento.*} o por variable de entorno.
 *
 * <p>Los valores por defecto son un punto de partida razonado, no una
 * verdad: la proporción entre ellos importa mucho más que su magnitud, porque
 * el score se normaliza al leer el perfil.
 */
@Component
@ConfigurationProperties(prefix = "descubrimiento")
@Getter
@Setter
public class PesosDescubrimiento {

    /** Peso de cada tipo de evento. Las señales negativas van en negativo. */
    private Map<TipoEvento, Double> pesos = pesosPorDefecto();

    /**
     * Cada cuánto se reduce a la mitad el peso de lo ya ocurrido.
     *
     * <p>Es lo que hace que el perfil evolucione: un interés de hace tres
     * semanas conserva un octavo de su fuerza, así que sigue contando pero no
     * manda sobre lo de ayer.
     */
    private Duration vidaMedia = Duration.ofDays(7);

    /**
     * Techo del tiempo de permanencia, en segundos.
     *
     * <p>Una pestaña olvidada no es interés. Sin techo, veinte minutos de
     * descuido pesarían más que una compra.
     */
    private int dwellMaximoSegundos = 180;

    /** Referencia para normalizar la permanencia; ver {@code factorDwell}. */
    private int dwellReferenciaSegundos = 30;

    /**
     * Cuántos eventos hacen falta para creerse una faceta.
     *
     * <p>Con {@code confianza = 1 − e^(−n/k)}: un solo evento vale 0,12 y no
     * llena el Home de macetas porque alguien miró una maceta.
     */
    private double confianzaK = 8.0;

    /** Mínimo de sujetos distintos para servir un dato agregado. */
    private int minimoSujetos = 50;

    /**
     * Personas distintas que hacen falta para que un producto cuente como
     * expuesto ante el freno del ranking.
     *
     * <h4>Por qué existe</h4>
     *
     * <p>El freno por exposición se aplica al ranking de TODOS los visitantes,
     * pero se alimentaba de un {@code COUNT(*)} sobre impresiones. Bastaba con
     * declarar muchas impresiones de un producto rival para hundirlo en el Home
     * de cualquiera. Con el piso, la exposición de una sola persona —o de unas
     * pocas— deja de contar, y frenar un producto exige que mucha gente lo haya
     * visto de verdad, que es justo lo que el freno quería medir.
     *
     * <h4>Por qué cinco y no cincuenta</h4>
     *
     * <p>No es el piso de privacidad de los agregados, que protege a las
     * personas y por eso es alto. Este protege al ranking de la manipulación, y
     * subirlo de más apagaría el freno en catálogos con poco tráfico: un
     * producto legítimamente popular en una tienda pequeña dejaría de frenarse
     * y acapararía la portada. Cinco personas distintas ya no las pone un
     * atacante sin acumular cinco identidades firmadas por el servidor.
     */
    private int exposicionMinimaSujetos = 5;

    /**
     * Escrituras de ingesta por sujeto y minuto.
     *
     * <p>El cupo por IP existía y no bastaba: 120 escrituras por minuto con
     * cien eventos cada una son doce mil eventos por minuto contra un mismo
     * perfil. Este límite acota el daño por identidad, que es la unidad en la
     * que se envenena un perfil.
     */
    private int ingestaPorSujetoPorMinuto = 60;

    /**
     * Sujetos nuevos que una misma IP puede estrenar por minuto.
     *
     * <p>Es lo que cierra la amplificación. Un cupo por sujeto no sirve de nada
     * si fabricar sujetos es gratis: quien quiera más cupo se inventa más
     * identidades. Desde que el servidor firma los sujetos, inventarlos exige
     * pedirlos, y pedirlos pasa por aquí.
     *
     * <p>Veinte por minuto y por IP deja pasar sin rozarla la navegación real
     * —un visitante estrena UN sujeto y lo reutiliza— y corta la creación en
     * masa desde una sola procedencia.
     */
    private int sujetosNuevosPorIpPorMinuto = 20;

    /**
     * Días que dura la memoria del enfriamiento. Ventana DESLIZANTE.
     *
     * <p>Deslizante es la palabra importante y es lo que hace que la
     * recuperación no necesite ningún proceso: una impresión de hace quince
     * días deja de contarse sola, el contador baja y el producto vuelve a
     * competir. Un «desfatigador» programado sería una tarea que puede fallar,
     * atrasarse o quedarse colgada, para conseguir exactamente lo mismo que
     * consigue no hacer nada.
     */
    private int diasSupresionPorFatiga = 14;

    /**
     * Cuánto enfría cada impresión sin respuesta, entre 0 y 1.
     *
     * <p>0,72 no es un número redondo y por eso está elegido: es el valor que
     * hace que {@code factorCooldown(3)} valga exactamente la mitad. Tres
     * impresiones sin que nadie las toque era el punto en el que la regla
     * anterior BLOQUEABA el producto; ahora es el punto en el que vale la mitad
     * y sigue compitiendo. La continuidad con la regla vieja es deliberada:
     * cambiar la forma de la curva y además su calibración a la vez habría
     * dejado sin saber cuál de las dos cosas movió los números.
     */
    private double penalizacionCooldown = 0.72;

    /**
     * Impresiones efectivas a partir de las cuales el ítem sale del módulo.
     *
     * <p>Seis, que es el doble del antiguo tope, y la razón de que sea el doble
     * es que bloquear y descontar no piden la misma evidencia. Descontar se
     * equivoca barato: si el sistema se pasa de frenada, el producto baja unos
     * puestos. Bloquear se equivoca caro: el producto desaparece de ese módulo
     * y no hay forma de que demuestre lo contrario hasta que la ventana corra.
     *
     * <p>Es {@code double} porque las impresiones de otros módulos cuentan
     * fraccionadas; ver {@link #cooldownCruzado}.
     */
    private double cooldownMaximo = 6.0;

    /**
     * Cuánto cuenta una impresión que ocurrió en OTRO módulo, entre 0 y 1.
     *
     * <p>Ni 1 ni 0, y las dos alternativas son peores. Con 1 el cooldown deja
     * de distinguir el módulo y volvemos a la regla anterior con más pasos:
     * haber visto algo tres veces en «relacionados» lo borraría de «lo más
     * popular», donde quizá nunca ha aparecido. Con 0 los módulos se ignoran
     * entre sí y un mismo producto puede perseguir a alguien por toda la
     * pantalla sin que ningún contador se entere.
     *
     * <p>0,35 dice lo que se quiere decir: una impresión en otro carrusel SÍ es
     * exposición —la persona ya lo ha visto— pero no es la misma insistencia
     * que repetirlo en el mismo sitio. Con este valor hacen falta dieciocho
     * apariciones ajenas para bloquear un módulo donde el producto no ha salido
     * nunca, que es tanto como decir que no pasa.
     */
    private double cooldownCruzado = 0.35;

    /** Cuánto del espacio se reserva a explorar en vez de acertar. */
    private double proporcionExploracion = 0.20;

    /** Cuánto se atenúa el interés al subir un nivel del árbol de categorías. */
    private double atenuacionPorNivel = 0.5;

    /** Máximo de ítems de la misma categoría dentro de un carrusel. */
    private int maximoPorCategoria = 3;

    /** Máximo de ítems de la misma marca dentro de un carrusel. */
    private int maximoPorMarca = 2;

    /* ══════════════ Fase 2 · señales colaborativas ══════════════ */

    /**
     * Ventana de conducta que alimenta el cálculo colaborativo, en días.
     *
     * <p>No es el histórico entero a propósito. Lo que la gente compraba junto
     * hace dos años describe un catálogo que ya no existe, y arrastrarlo
     * convierte al recomendador en un archivo. Treinta días es corto para que
     * refleje la temporada y largo para que un producto de rotación lenta
     * acumule evidencia.
     */
    private int colaborativoVentanaDias = 30;

    /**
     * Sujetos distintos que tienen que sostener una relación entre productos.
     *
     * <p>Con uno solo, cualquier casualidad se convierte en recomendación: dos
     * pestañas abiertas por la misma persona bastarían para emparejar un
     * teclado con una silla. Tres es el mínimo que ya no es anécdota.
     */
    private int colaborativoMinSoporte = 3;

    /**
     * Ítems como máximo que aporta un sujeto al cálculo de co-interacción.
     *
     * <p>Acota el autojoin, que es cuadrático en los ítems de CADA sujeto. Sin
     * tope, una sola sesión anómala —un rastreador, alguien comparando medio
     * catálogo— multiplica el coste del proceso por lotes.
     */
    private int colaborativoTopeItemsPorSujeto = 200;

    /** Facetas que dos sujetos tienen que compartir para considerarlos parecidos. */
    private int similitudMinCompartidas = 3;

    /**
     * A partir de cuántos sujetos una faceta deja de servir para emparejar.
     *
     * <p>Una faceta que tiene casi todo el mundo no distingue a nadie —saber
     * que a dos personas les interesa la tecnología en una tienda de tecnología
     * no dice nada— y además es la que haría explotar el cruce. Descartarla
     * gana rendimiento y precisión a la vez.
     */
    private int similitudTopeSujetosPorFaceta = 500;

    /** Por debajo de este parecido, un vecino no aporta nada que fiarse. */
    private double similitudMinima = 0.15;

    /** Vecinos que se consultan como mucho al generar candidatos. */
    private int similitudVecinosConsultados = 25;

    /**
     * Vecinos distintos que tienen que haber tocado un producto para ofrecerlo.
     *
     * <p>Es el piso de privacidad del módulo de perfiles parecidos, y no es lo
     * mismo que el mínimo de 50 sujetos de las tendencias geográficas: allí se
     * protege un dato de zona, aquí se evita que el carrusel de una persona sea
     * el historial de otra. Con uno solo bastaría para que «otras personas
     * descubrieron» significara «una persona concreta miró».
     *
     * <p>Con la tienda recién abierta esto devuelve vacío casi siempre, y está
     * bien que así sea: el Home cae a los otros módulos y nadie queda expuesto
     * por haber sido de los primeros en llegar.
     */
    private int similitudMinAportantes = 3;

    /**
     * Peso de cada origen al combinar candidatos de distintos generadores.
     *
     * <p>Son la única calibración del ranker híbrido y viven aquí y en ningún
     * otro sitio: repartir números por varias clases es como se pierde la
     * capacidad de ajustar el sistema. Lo que importa es la PROPORCIÓN, porque
     * el score de cada generador se normaliza antes de aplicarlos.
     *
     * <p>El orden de partida dice qué se cree más: lo que esta persona ya ha
     * demostrado que le interesa, después lo que hace gente con su mismo gusto,
     * después el parecido de contenido, y al final lo que solo es popular.
     */
    private Map<Origen, Double> pesoOrigen = pesosDeOrigenPorDefecto();

    /**
     * Cuánto se castiga a un producto por ser popular, entre 0 y 1.
     *
     * <p>Con 0 no se castiga nada y el catálogo se convierte en un embudo: lo
     * que ya se ve, se recomienda; al recomendarse, se ve más. Con 1 se anula
     * la popularidad, que tampoco es cierto —algo puede ser popular porque es
     * bueno—. El factor aplicado es {@code 1 / (1 + k·ln(1+impresiones))}.
     */
    private double penalizacionPopularidad = 0.35;

    /** Tope de carruseles colaborativos en el Home. */
    private int maximoModulosColaborativos = 1;

    /* ══════════════ Temporalidad y sesión ══════════════ */

    /**
     * Cuánto tiempo sin actividad da una sesión por terminada.
     *
     * <p>Treinta minutos, que es la convención de toda la analítica web y encaja
     * con lo que hace el cliente: cada carga de la aplicación estrena un
     * {@code sesionId}, y una pestaña abierta conserva el suyo. Sirve para
     * decidir qué sesión está VIVA, porque el identificador de sesión no viaja
     * en las peticiones de lectura y hay que deducirlo del último evento.
     */
    private int sesionVentanaMinutos = 30;

    /**
     * Qué se considera «interés reciente», en horas.
     *
     * <p>Veinticuatro, y sale de la vida media que ya existe, no de una
     * corazonada. Con siete días de vida media, un evento de hace un día
     * conserva {@code 2^(-1/7) ≈ 0,91} de su peso: el olvido todavía no ha
     * hecho prácticamente nada. «Reciente» es exactamente esa franja — aquello
     * que el perfil aún no ha empezado a difuminar.
     *
     * <p>A los dos días serían 0,82 y a los siete 0,50, que ya es otra cosa.
     */
    private int interesRecienteHoras = 24;

    /**
     * Interacciones mínimas para creerse una intención de sesión.
     *
     * <p>Con una sola, cualquier clic accidental convertiría el Home entero en
     * un monográfico. Dos es el mínimo que ya no es un resbalón, y por debajo de
     * eso el sistema hace lo que hacía: tendencia, zona, exploración.
     */
    private int sesionMinimasInteracciones = 2;

    /** Cuántas categorías de la sesión se usan para buscar candidatos. */
    private int sesionMaximasFacetas = 3;

    /* ══════════════ Catálogo nuevo ══════════════ */

    /**
     * Cuántos días se considera nuevo un producto.
     *
     * <p>Treinta, y no es un número al azar: es la misma ventana que usa el
     * cálculo colaborativo. «Nuevo» significa entonces algo concreto y
     * defendible — que todavía no ha tenido tiempo de acumular la evidencia de
     * conducta con la que se recomienda todo lo demás. Que las dos ventanas
     * coincidan no es estética: es que describen el mismo umbral desde los dos
     * lados.
     *
     * <p>La cuenta se hace SIEMPRE contra {@code creado_en}, nunca contra el
     * {@code id}. Un identificador alto solo dice que la fila se insertó
     * después; la semilla metió sesenta y ocho productos en un segundo.
     */
    private int catalogoNuevoDias = 30;

    /**
     * Cuántos productos nuevos como mucho pueden entrar en un carrusel.
     *
     * <p>Un tope en unidades y no un porcentaje: con carruseles de doce, un
     * porcentaje da fracciones que hay que redondear y el número deja de
     * poderse razonar. Dos de doce es aproximadamente un sexto.
     *
     * <p>Es un TECHO de candidatos, no un suelo de huecos. Los productos nuevos
     * entran a competir y el ranker decide; pueden acabar sin aparecer. Eso es
     * lo correcto: el cupo existe para que la novedad no desplace a la
     * relevancia, no para garantizarle sitio a nada.
     */
    private int catalogoNuevoCupo = 2;

    /** El instante a partir del cual un producto cuenta como nuevo. */
    public java.time.Instant fronteraCatalogoNuevo() {
        return java.time.Instant.now().minus(Duration.ofDays(catalogoNuevoDias));
    }

    /* ══════════════ Fase 3 · medición ══════════════ */

    /**
     * Etiqueta de la configuración de ranking en curso.
     *
     * <p>Es la mitad legible de la versión; la otra mitad se calcula. Ver
     * {@link #rankerVersion()}.
     */
    private String rankerEtiqueta = "v3.0";

    /**
     * Días que se conserva el DETALLE de lo servido y lo visto.
     *
     * <p>Crece con el tráfico —decenas de filas por visita— y solo hace falta
     * mientras se pueda querer reevaluar. El agregado diario, que es lo que
     * permite comparar con el año pasado, no se purga nunca.
     */
    private int retencionDias = 90;

    /**
     * Filas por lote al purgar eventos.
     *
     * <p>Cada lote es una transacción propia. Cinco mil filas se borran en
     * milisegundos por el índice de fecha y no retienen nada que estorbe a la
     * ingesta que sigue entrando. Es un valor inicial sin dato previo detrás:
     * si un lote tarda más de lo razonable, la medida es bajarlo, no subirlo.
     */
    private int purgaLote = 5000;

    /**
     * Techo de filas que borra UNA pasada del mantenimiento.
     *
     * <p>Acota cuánto trabajo hace una ejecución; lo que quede lo termina la
     * siguiente. Existe por la primera pasada tras desplegar la purga, que se
     * encuentra con todo el atraso acumulado desde la fase 1: sin tope, esa
     * pasada haría de una sola vez lo que después será una hora de eventos.
     */
    private int purgaMaximoPorEjecucion = 200_000;

    /**
     * La versión que se graba con cada recomendación servida.
     *
     * <p>Etiqueta MÁS huella de los pesos efectivos, por ejemplo
     * {@code v3.0-4f2a1c}. La huella no es un adorno: una etiqueta a mano se
     * olvida de subir, y entonces dos configuraciones distintas quedan
     * registradas con el mismo nombre. Eso es peor que no versionar, porque la
     * comparación posterior parece válida y no lo es. Cambiando cualquier peso
     * cambia la huella, se acuerde alguien o no.
     *
     * <p>Entran solo los números que afectan al ORDEN: los pesos por origen y
     * el castigo por popularidad. La vida media o el techo de permanencia
     * cambian el perfil, no la forma de combinarlo, y meterlos aquí haría que la
     * versión bailara por motivos que no explican una diferencia de ranking.
     */
    public String rankerVersion() {
        StringBuilder huella = new StringBuilder();
        for (Origen origen : Origen.values()) {
            huella.append(origen.name()).append('=').append(pesoDe(origen)).append(';');
        }
        huella.append("pop=").append(penalizacionPopularidad);

        // SHA-256 recortado: no es criptografía, es un identificador estable.
        // Basta con que dos configuraciones distintas no coincidan.
        try {
            byte[] resumen = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(huella.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append(String.format("%02x", resumen[i]));
            }
            return rankerEtiqueta + "-" + hex;
        } catch (java.security.NoSuchAlgorithmException imposible) {
            // SHA-256 lo exige la especificación de Java desde siempre.
            throw new IllegalStateException(imposible);
        }
    }

    private static Map<Origen, Double> pesosDeOrigenPorDefecto() {
        Map<Origen, Double> p = new EnumMap<>(Origen.class);
        p.put(Origen.PERSONAL, 1.0);
        p.put(Origen.COHORTE, 0.8);
        p.put(Origen.GEO, 0.5);
        p.put(Origen.TENDENCIA, 0.4);
        p.put(Origen.EXPLORACION, 0.3);
        return p;
    }

    public double pesoDe(Origen origen) {
        return pesoOrigen.getOrDefault(origen, 0.0);
    }

    /**
     * El factor por popularidad, ya acotado entre 0 y 1.
     *
     * <p>Logarítmico porque la diferencia entre 10 y 100 impresiones importa, y
     * la que hay entre 10.000 y 100.000 ya no.
     */
    public double factorPopularidad(long impresiones) {
        if (impresiones <= 0) {
            return 1.0;
        }
        return 1.0 / (1.0 + penalizacionPopularidad * Math.log1p(impresiones));
    }

    /**
     * Cuánto conserva un candidato tras haber sido mostrado sin respuesta.
     *
     * <h4>Por qué una curva y no un escalón</h4>
     *
     * <p>La regla anterior era un escalón en tres: cero, una y dos impresiones
     * valían exactamente lo mismo, y la tercera borraba el producto durante dos
     * semanas. Las dos mitades de esa frase están mal. Por abajo, insistir dos
     * veces con algo que nadie toca no puede costar lo mismo que no haberlo
     * enseñado nunca. Por arriba, una exposición pasada se convertía en una
     * sentencia: el producto no bajaba de puesto, desaparecía, y ya no había
     * manera de que demostrara nada.
     *
     * <h4>La misma familia que ya usa el proyecto</h4>
     *
     * <p>{@code 1 / (1 + k·ln(1+n))}, que es la forma de {@link
     * #factorPopularidad} y la del freno por exposición del ranker adaptativo.
     * No se repite por inercia: es la forma correcta para algo que tiene que
     * reaccionar mucho a las primeras repeticiones y poco a las siguientes. La
     * diferencia entre haber visto algo una vez y tres importa; entre treinta y
     * cuarenta, no.
     *
     * <p>Propiedades, que están probadas y no solo escritas aquí:
     * {@code f(0) = 1} exacto —sin impresiones no hay castigo—, {@code f} es
     * estrictamente decreciente, y nunca llega a cero, porque el cero lo pone
     * el corte de {@link #getCooldownMaximo()} y no una asíntota.
     *
     * @param impresiones impresiones efectivas, que pueden ser fraccionarias
     *     porque las de otros módulos cuentan a peso reducido
     */
    public double factorCooldown(double impresiones) {
        if (impresiones <= 0) {
            return 1.0;
        }
        return 1.0 / (1.0 + penalizacionCooldown * Math.log1p(impresiones));
    }

    /** La ventana colaborativa como duración, que es como la usa el proceso. */
    public Duration ventanaColaborativa() {
        return Duration.ofDays(colaborativoVentanaDias);
    }

    /**
     * El factor por permanencia, ya acotado.
     *
     * <p>Logarítmico y con techo: 10 s dan 1,28; 60 s dan 1,95; y media hora
     * da lo mismo que tres minutos, que es lo que se quiere.
     */
    public double factorDwell(Integer dwellMs) {
        if (dwellMs == null || dwellMs <= 0) {
            return 1.0;
        }
        double segundos = Math.min(dwellMs / 1000.0, dwellMaximoSegundos);
        return 1.0 + Math.log1p(segundos / dwellReferenciaSegundos);
    }

    /**
     * La saturación del n-ésimo evento igual en la misma sesión.
     *
     * <p>Refrescar una ficha veinte veces no son veinte intereses. El primero
     * vale 1,00; el tercero 0,48; el décimo 0,30.
     */
    public double factorSaturacion(int repeticion) {
        return 1.0 / (1.0 + Math.log(Math.max(1, repeticion)));
    }

    /** Cuánta evidencia sostiene una faceta, entre 0 y 1. */
    public double confianza(int eventos) {
        return 1.0 - Math.exp(-eventos / confianzaK);
    }

    public double pesoDe(TipoEvento tipo) {
        return pesos.getOrDefault(tipo, 0.0);
    }

    private static Map<TipoEvento, Double> pesosPorDefecto() {
        Map<TipoEvento, Double> p = new EnumMap<>(TipoEvento.class);
        p.put(TipoEvento.ITEM_VIEW, 1.0);
        p.put(TipoEvento.CATEGORY_VIEW, 0.5);
        p.put(TipoEvento.ITEM_VIEW_DEEP, 2.5);
        p.put(TipoEvento.SEARCH, 2.0);
        p.put(TipoEvento.SEARCH_CLICK, 3.0);
        p.put(TipoEvento.ATTRIBUTE_FILTER, 3.0);
        p.put(TipoEvento.COMPARE, 5.0);
        p.put(TipoEvento.SHARE, 6.0);
        p.put(TipoEvento.FAVORITE, 8.0);
        p.put(TipoEvento.ADD_TO_CART, 12.0);
        p.put(TipoEvento.REVIEW, 15.0);
        p.put(TipoEvento.PURCHASE, 20.0);
        p.put(TipoEvento.IMPRESSION, -0.1);
        p.put(TipoEvento.REMOVE_FROM_CART, -4.0);
        p.put(TipoEvento.DISMISS, -10.0);
        p.put(TipoEvento.NOT_INTERESTED, -25.0);
        return p;
    }
}
