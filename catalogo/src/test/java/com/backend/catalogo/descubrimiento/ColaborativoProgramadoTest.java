package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * La anotación que se cayó una vez y no lo vio ninguna prueba.
 *
 * <p>El proceso por lotes fallaba en cada pasada —«No active transaction for
 * update or delete query»— y las tablas colaborativas se quedaban vacías, en
 * silencio, mientras el resto del Home seguía funcionando. La causa es la
 * autoinvocación: el planificador entra por el proxy de Spring, y de ahí en
 * adelante la llamada interna a {@code recalcular()} va sobre el objeto real,
 * donde la anotación transaccional ya no pinta nada.
 *
 * <p>Las pruebas de integración no podían verlo, y conviene entender por qué:
 * llaman a {@code recalcular()} desde fuera, que es a través del proxy. Prueban
 * el camino correcto y no el que se recorre en producción.
 *
 * <p>Por eso esta prueba mira la ESTRUCTURA y no el comportamiento. Es fea, y
 * es la única forma de que quitar esa anotación rompa algo.
 */
@DisplayName("El lote colaborativo, por dentro")
class ColaborativoProgramadoTest {

    @Test
    @DisplayName("la pasada programada abre transacción por sí misma")
    void elMetodoProgramadoEsTransaccional() throws Exception {
        Method programado = ColaborativoService.class.getMethod("programado");

        assertThat(programado.isAnnotationPresent(Scheduled.class))
                .as("sigue siendo el que dispara el reloj")
                .isTrue();

        assertThat(programado.isAnnotationPresent(Transactional.class))
                .as("sin esto, cada pasada falla y las tablas se quedan vacías")
                .isTrue();
    }
}
