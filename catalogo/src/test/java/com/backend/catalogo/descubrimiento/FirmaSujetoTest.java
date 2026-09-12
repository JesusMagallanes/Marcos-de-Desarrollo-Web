package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Que el identificador de sujeto haya que poseerlo, no conocerlo.
 *
 * <p>Antes de H, el identificador anónimo era una credencial sin protección:
 * quien lo copiara de un log o de una URL se llevaba esa identidad entera. Estas
 * pruebas fijan las tres cosas de las que la firma tiene que proteger —cambiar
 * el identificador, inventarse uno y usar el de otro— y una cuarta que no se ve
 * desde fuera: que el secreto de un servidor no sirva en otro.
 */
@DisplayName("Firma del sujeto")
class FirmaSujetoTest {

    private final FirmaSujeto firmas = new FirmaSujeto("secreto-de-prueba-uno");

    @Nested
    @DisplayName("Lo que acepta")
    class LoQueAcepta {

        @Test
        @DisplayName("la firma que ella misma emitió")
        void laPropia() {
            UUID sujeto = UUID.randomUUID();
            assertThat(firmas.valida(sujeto, firmas.de(sujeto))).isTrue();
        }

        @Test
        @DisplayName("y la emite igual cada vez, para que el cliente pueda guardarla")
        void esEstable() {
            /*
             * Si cambiara en cada peticion, el cliente tendria que refrescarla
             * constantemente y una respuesta perdida lo dejaria fuera de su
             * propia identidad.
             */
            UUID sujeto = UUID.randomUUID();
            assertThat(firmas.de(sujeto)).isEqualTo(firmas.de(sujeto));
        }
    }

    @Nested
    @DisplayName("Lo que rechaza")
    class LoQueRechaza {

        @Test
        @DisplayName("cambiar el identificador invalida la firma")
        void noSirveParaOtroSujeto() {
            UUID mio = UUID.randomUUID();
            UUID ajeno = UUID.randomUUID();

            assertThat(firmas.valida(ajeno, firmas.de(mio)))
                    .as("la firma va atada a SU identificador")
                    .isFalse();
        }

        @Test
        @DisplayName("un identificador inventado no trae firma que valga")
        void noSeInventaUnSujeto() {
            UUID inventado = UUID.randomUUID();

            assertThat(firmas.valida(inventado, "cualquier-cosa")).isFalse();
            assertThat(firmas.valida(inventado, "")).isFalse();
            assertThat(firmas.valida(inventado, null)).isFalse();
        }

        @Test
        @DisplayName("manipular un solo carácter la tumba")
        void noSeRetoca() {
            UUID sujeto = UUID.randomUUID();
            String buena = firmas.de(sujeto);
            String tocada = (buena.charAt(0) == 'A' ? 'B' : 'A') + buena.substring(1);

            assertThat(firmas.valida(sujeto, tocada)).isFalse();
        }

        @Test
        @DisplayName("una firma truncada no cuela")
        void noSeRecorta() {
            /*
             * Importa porque una comparacion ingenua por prefijo aceptaria esto,
             * y es el error clasico al escribir la verificacion a mano.
             */
            UUID sujeto = UUID.randomUUID();
            String buena = firmas.de(sujeto);

            assertThat(firmas.valida(sujeto, buena.substring(0, buena.length() - 1))).isFalse();
            assertThat(firmas.valida(sujeto, buena + "x")).isFalse();
        }

        @Test
        @DisplayName("la firma de otro servidor no vale aquí")
        void noViajaEntreSecretos() {
            FirmaSujeto otroServidor = new FirmaSujeto("secreto-de-prueba-dos");
            UUID sujeto = UUID.randomUUID();

            assertThat(firmas.valida(sujeto, otroServidor.de(sujeto))).isFalse();
        }

        @Test
        @DisplayName("sin identificador no hay nada que validar")
        void sinSujeto() {
            assertThat(firmas.valida(null, "lo que sea")).isFalse();
        }
    }

    @Nested
    @DisplayName("Arranque")
    class Arranque {

        @Test
        @DisplayName("sin secreto el servicio no arranca, en vez de firmar con nada")
        void exigeSecreto() {
            /*
             * Fallar al arrancar es lo correcto. Un secreto vacio produciria
             * firmas validas y predecibles, y el sistema parecería seguro
             * mientras no protege nada — que es peor que no tener firma.
             */
            assertThatThrownBy(() -> new FirmaSujeto(""))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new FirmaSujeto(null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("el secreto no se puede deducir de la firma")
        void noFiltraElSecreto() {
            UUID sujeto = UUID.randomUUID();
            String firma = firmas.de(sujeto);

            assertThat(firma)
                    .doesNotContain("secreto-de-prueba-uno")
                    .doesNotContain(sujeto.toString());
        }
    }
}
