package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * Las transformaciones que convierten comportamiento en interés.
 *
 * <p>Son cuatro funciones puras y son el corazón del sistema: si el tiempo de
 * permanencia no tiene techo, una pestaña olvidada pesa más que una compra; si
 * no hay saturación, refrescar veinte veces son veinte intereses; y sin
 * confianza, un solo evento convierte una casualidad en el interés dominante.
 */
@DisplayName("Ponderación del descubrimiento")
class PesosDescubrimientoTest {

    private final PesosDescubrimiento pesos = new PesosDescubrimiento();

    @Nested
    @DisplayName("Tiempo de permanencia")
    class Permanencia {

        @Test
        @DisplayName("una pestaña olvidada NO vale más que una lectura atenta")
        void tieneTecho() {
            /*
             * El caso real: alguien abre una ficha, se va a almorzar y vuelve
             * media hora después. Sin techo eso sería la señal más fuerte de
             * toda su sesión, y es justo la que menos significa.
             */
            double tresMinutos = pesos.factorDwell(180_000);
            double mediaHora = pesos.factorDwell(1_800_000);

            assertThat(mediaHora).isEqualTo(tresMinutos);
            assertThat(mediaHora).isLessThan(3.0);
        }

        @Test
        @DisplayName("crece, pero cada vez menos")
        void esLogaritmico() {
            double diezSegundos = pesos.factorDwell(10_000);
            double unMinuto = pesos.factorDwell(60_000);
            double dosMinutos = pesos.factorDwell(120_000);

            assertThat(diezSegundos).isLessThan(unMinuto).isLessThan(dosMinutos);
            // Seis veces más tiempo no da seis veces más señal.
            assertThat(unMinuto / diezSegundos).isLessThan(2.0);
        }

        @Test
        @DisplayName("sin dato de tiempo no penaliza ni premia")
        void sinDatoEsNeutro() {
            assertThat(pesos.factorDwell(null)).isEqualTo(1.0);
            assertThat(pesos.factorDwell(0)).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Saturación")
    class Saturacion {

        @Test
        @DisplayName("ver lo mismo veinte veces no son veinte intereses")
        void decreceConLaRepeticion() {
            assertThat(pesos.factorSaturacion(1)).isEqualTo(1.0);
            assertThat(pesos.factorSaturacion(3)).isLessThan(0.6);
            assertThat(pesos.factorSaturacion(10)).isLessThan(0.35);
            // Pero nunca llega a cero: insistir sigue diciendo algo.
            assertThat(pesos.factorSaturacion(100)).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Confianza")
    class Confianza {

        @Test
        @DisplayName("un solo evento no llena el Home de macetas")
        void unEventoPesaPoco() {
            /*
             * Alguien mira UN producto de jardinería. Sin ponderar por
             * evidencia, su afinidad con jardinería sería 1.0 —es el único
             * dato— y el Home entero se llenaría de macetas.
             */
            assertThat(pesos.confianza(1)).isLessThan(0.15);
            assertThat(pesos.confianza(8)).isBetween(0.6, 0.7);
            assertThat(pesos.confianza(30)).isGreaterThan(0.97);
        }

        @Test
        @DisplayName("crece siempre, y nunca pasa de uno")
        void estaAcotada() {
            assertThat(pesos.confianza(0)).isEqualTo(0.0);
            assertThat(pesos.confianza(1000)).isLessThanOrEqualTo(1.0);
            assertThat(pesos.confianza(5)).isLessThan(pesos.confianza(6));
        }
    }

    @Nested
    @DisplayName("Pesos por tipo de evento")
    class Pesado {

        @Test
        @DisplayName("las señales negativas restan de verdad")
        void negativas() {
            assertThat(pesos.pesoDe(TipoEvento.NOT_INTERESTED)).isNegative();
            assertThat(pesos.pesoDe(TipoEvento.DISMISS)).isNegative();
            assertThat(pesos.pesoDe(TipoEvento.IMPRESSION)).isNegative();
            // «No me interesa» tiene que doler más que cerrar una tarjeta.
            assertThat(pesos.pesoDe(TipoEvento.NOT_INTERESTED))
                    .isLessThan(pesos.pesoDe(TipoEvento.DISMISS));
        }

        @Test
        @DisplayName("filtrar dice más que mirar")
        void filtrarPesaMasQueVer() {
            /*
             * Quien filtra «27 pulgadas» declaró el atributo Y el valor
             * exactos. Es la señal más específica que se puede capturar sin
             * preguntar, y solo es aprovechable porque los atributos son filas
             * en producto_atributo.
             */
            assertThat(pesos.pesoDe(TipoEvento.ATTRIBUTE_FILTER))
                    .isGreaterThan(pesos.pesoDe(TipoEvento.ITEM_VIEW))
                    .isGreaterThan(pesos.pesoDe(TipoEvento.CATEGORY_VIEW));
        }

        @Test
        @DisplayName("el orden de intención se respeta de mirar a comprar")
        void escalaCoherente() {
            assertThat(pesos.pesoDe(TipoEvento.ITEM_VIEW))
                    .isLessThan(pesos.pesoDe(TipoEvento.FAVORITE));
            assertThat(pesos.pesoDe(TipoEvento.FAVORITE))
                    .isLessThan(pesos.pesoDe(TipoEvento.ADD_TO_CART));
            assertThat(pesos.pesoDe(TipoEvento.ADD_TO_CART))
                    .isLessThan(pesos.pesoDe(TipoEvento.PURCHASE));
        }

        @Test
        @DisplayName("se pueden recalibrar sin tocar el código")
        void sonConfigurables() {
            // Es el punto de tenerlos fuera del enum: calibrar es lo primero
            // que hay que hacer cuando llegan datos reales.
            pesos.getPesos().put(TipoEvento.ITEM_VIEW, 9.9);
            assertThat(pesos.pesoDe(TipoEvento.ITEM_VIEW)).isEqualTo(9.9);
        }
    }

    @Test
    @DisplayName("la vida media por defecto deja evolucionar el perfil")
    void vidaMediaRazonable() {
        // Ni tan corta que olvide de un día para otro, ni tan larga que un
        // interés de hace medio año siga mandando.
        assertThat(pesos.getVidaMedia()).isBetween(Duration.ofDays(3), Duration.ofDays(30));
    }
}
