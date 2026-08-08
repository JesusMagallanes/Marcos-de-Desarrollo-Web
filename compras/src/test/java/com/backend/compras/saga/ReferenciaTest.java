package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.backend.compras.shared.error.ConflictoException;

/**
 * La referencia es lo que ata un pago de MercadoPago a su usuario y a su medio
 * de pago. Si se pudiera falsificar o malinterpretar, se podría confirmar el
 * pago de otra persona.
 */
class ReferenciaTest {

    @Test
    @DisplayName("ida y vuelta conserva usuario y método de pago")
    void idaYVuelta() {
        Referencia original = Referencia.crear(42L, 7L);
        Referencia recuperada = Referencia.parsear(original.formatear());

        assertThat(recuperada.usuarioId()).isEqualTo(42L);
        assertThat(recuperada.metodoPagoId()).isEqualTo(7L);
        assertThat(recuperada.marcaTiempo()).isEqualTo(original.marcaTiempo());
    }

    @Test
    @DisplayName("el formato lleva el prefijo del sistema")
    void formato() {
        assertThat(Referencia.crear(1L, 2L).formatear()).startsWith("sz-1-2-");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("un pago sin referencia no se puede conciliar")
    void referenciaAusente(String crudo) {
        assertThatThrownBy(() -> Referencia.parsear(crudo))
                .isInstanceOf(ConflictoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "otro-1-2-3",       // prefijo ajeno
            "sz-1-2",           // faltan partes
            "sz-1-2-3-4",       // sobran partes
            "sz-abc-2-3",       // usuario no numérico
            "sz-1-x-3",         // método de pago no numérico
            "'; DROP TABLE pedido;--"
    })
    @DisplayName("referencias malformadas se rechazan en vez de interpretarse a medias")
    void referenciasInvalidas(String crudo) {
        assertThatThrownBy(() -> Referencia.parsear(crudo))
                .isInstanceOf(ConflictoException.class);
    }
}
