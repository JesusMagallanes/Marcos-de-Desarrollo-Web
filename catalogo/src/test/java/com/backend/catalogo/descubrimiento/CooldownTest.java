package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * Las propiedades de la curva, sin base de datos de por medio.
 *
 * <p>Una fórmula escrita en un comentario no es una garantía. Aquí se comprueba
 * que el enfriamiento cumple lo que la fase exige —arranca en uno, decrece
 * siempre y nunca se da la vuelta— y que el corte está donde dice estar. Si
 * mañana alguien cambia la constante, estas pruebas son las que dicen si la
 * cambió dentro de lo aceptable o rompió la propiedad.
 */
@DisplayName("Enfriamiento · la curva")
class CooldownTest {

    private final PesosDescubrimiento pesos = new PesosDescubrimiento();

    @Nested
    @DisplayName("Fronteras")
    class Fronteras {

        @Test
        @DisplayName("sin impresiones el candidato vale exactamente lo que valía")
        void sinImpresionesNoHayCastigo() {
            /*
             * Exactamente 1, no «casi 1». Es la frontera que separa «no se ha
             * enseñado» de «se ha enseñado y no interesó», y una aproximacion
             * aqui significaria que el sistema descuenta algo a todo el
             * catalogo por existir.
             */
            assertThat(pesos.factorCooldown(0)).isEqualTo(1.0);
            assertThat(pesos.factorCooldown(-3)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("cada impresión más pesa más que la anterior")
        void masExposicionMasCastigo() {
            double cero = pesos.factorCooldown(0);
            double una = pesos.factorCooldown(1);
            double dos = pesos.factorCooldown(2);
            double tres = pesos.factorCooldown(3);

            assertThat(una).isLessThan(cero);
            assertThat(dos).isLessThan(una);
            assertThat(tres).isLessThan(dos);
        }

        @Test
        @DisplayName("con tres impresiones vale la mitad, y sigue en la lista")
        void tresImpresionesValenLaMitad() {
            /*
             * Tres era el punto donde la regla anterior BORRABA el producto.
             * Aqui vale la mitad y compite: si aun asi gana, es que merecia
             * estar. Esa continuidad es lo que permite comparar el antes y el
             * despues sin haber cambiado dos cosas a la vez.
             */
            assertThat(pesos.factorCooldown(3)).isCloseTo(0.5, within(0.01));
            assertThat(3.0).isLessThan(pesos.getCooldownMaximo());
        }

        @Test
        @DisplayName("el corte está en el doble del antiguo tope")
        void elCorteEstaDondeDice() {
            assertThat(pesos.getCooldownMaximo()).isEqualTo(6.0);
            // Todavia dentro, y por tanto todavia con score que descontar.
            assertThat(pesos.factorCooldown(5)).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Propiedades")
    class Propiedades {

        @Test
        @DisplayName("decrece siempre, sin rebotes, hasta muy lejos del corte")
        void decreceSiempre() {
            double anterior = pesos.factorCooldown(0);
            for (int n = 1; n <= 200; n++) {
                double actual = pesos.factorCooldown(n);
                assertThat(actual)
                        .as("la curva no puede darse la vuelta en ningún punto")
                        .isLessThan(anterior);
                anterior = actual;
            }
        }

        @Test
        @DisplayName("nunca llega a cero: quien excluye es el corte, no la curva")
        void nuncaLlegaACero() {
            /*
             * Importa quien toma la decision. Si la curva se anulara sola, un
             * producto podria quedar fuera por redondeo y nadie sabria decir a
             * partir de cuando. El corte es un numero configurado y visible.
             */
            assertThat(pesos.factorCooldown(100_000)).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("la misma entrada da siempre el mismo resultado")
        void esDeterminista() {
            for (int n = 0; n <= 12; n++) {
                assertThat(pesos.factorCooldown(n)).isEqualTo(pesos.factorCooldown(n));
            }
        }

        @Test
        @DisplayName("una impresión de otro carrusel pesa, pero pesa menos")
        void lasAjenasPesanMenos() {
            /*
             * La mecanica del peso cruzado: tres impresiones ajenas equivalen a
             * 1,05 propias. Castigan —la persona ya ha visto el producto— pero
             * no como insistir en el mismo sitio.
             */
            double ajenas = 3 * pesos.getCooldownCruzado();

            assertThat(pesos.factorCooldown(ajenas))
                    .isGreaterThan(pesos.factorCooldown(3))
                    .isLessThan(pesos.factorCooldown(0));
            assertThat(ajenas).isLessThan(pesos.getCooldownMaximo());
        }

        @Test
        @DisplayName("hacen falta dieciocho apariciones ajenas para bloquear un carrusel")
        void bloquearDesdeFueraEsCasiImposible() {
            /*
             * Es el limite que hace honesto llamar a esto «cooldown por
             * modulo»: se puede llegar al corte sin haber salido nunca en ese
             * carrusel, pero hace falta tal cantidad de exposicion en el resto
             * de la pantalla que, si ocurre, bloquear es lo correcto.
             */
            int hacenFalta = 0;
            while (hacenFalta * pesos.getCooldownCruzado() < pesos.getCooldownMaximo()) {
                hacenFalta++;
            }
            assertThat(hacenFalta).isEqualTo(18);
        }
    }
}
