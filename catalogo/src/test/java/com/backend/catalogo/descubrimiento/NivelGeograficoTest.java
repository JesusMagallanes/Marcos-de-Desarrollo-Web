package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La escalera de degradación geográfica, sobre el ubigeo del INEI que ya vive
 * en {@code usuarios.ubigeo}.
 *
 * <p>Ica no es Lima: en un distrito puede no haber gente suficiente para que
 * una tendencia signifique algo. Subir un peldaño es a la vez un control de
 * privacidad —con cuatro personas la «tendencia» delata a quien la generó— y de
 * validez estadística.
 */
@DisplayName("Degradación geográfica")
class NivelGeograficoTest {

    /** Ica: departamento 11, provincia 1101, distrito 110101. */
    private static final String ICA = "110101";

    @Test
    @DisplayName("cada nivel recorta el ubigeo por donde le toca")
    void recorta() {
        assertThat(NivelGeografico.DISTRITO.zonaDe(ICA)).isEqualTo("110101");
        assertThat(NivelGeografico.PROVINCIA.zonaDe(ICA)).isEqualTo("1101");
        assertThat(NivelGeografico.DEPARTAMENTO.zonaDe(ICA)).isEqualTo("11");
        assertThat(NivelGeografico.NACIONAL.zonaDe(ICA)).isEmpty();
    }

    @Test
    @DisplayName("la escalera sube hasta nacional y ahí se acaba")
    void sube() {
        assertThat(NivelGeografico.DISTRITO.siguiente()).isEqualTo(NivelGeografico.PROVINCIA);
        assertThat(NivelGeografico.PROVINCIA.siguiente()).isEqualTo(NivelGeografico.DEPARTAMENTO);
        assertThat(NivelGeografico.DEPARTAMENTO.siguiente()).isEqualTo(NivelGeografico.NACIONAL);
        // Sin esto, el bucle que degrada no tendría condición de salida.
        assertThat(NivelGeografico.NACIONAL.siguiente()).isNull();
    }

    @Test
    @DisplayName("sin ubigeo no se inventa una zona")
    void sinUbigeo() {
        // Devolver un prefijo a medias metería al sujeto en la zona de otro.
        assertThat(NivelGeografico.DISTRITO.zonaDe(null)).isEmpty();
        assertThat(NivelGeografico.DISTRITO.zonaDe("11")).isEmpty();
        assertThat(NivelGeografico.NACIONAL.zonaDe(null)).isEmpty();
    }
}
