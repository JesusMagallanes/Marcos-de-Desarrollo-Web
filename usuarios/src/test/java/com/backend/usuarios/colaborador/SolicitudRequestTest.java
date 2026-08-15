package com.backend.usuarios.colaborador;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backend.usuarios.colaborador.dto.SolicitudDtos.Domicilio;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudRequest;
import com.backend.usuarios.shared.error.DatosInvalidosException;

/**
 * Las reglas que cruzan campos.
 *
 * <p>Son las que ninguna anotación puede expresar, porque miran dos o tres
 * campos a la vez: que el documento case con el tipo de solicitante, que la
 * empresa declare quién firma por ella, que la persona sea mayor de edad.
 */
class SolicitudRequestTest {

    private static final Domicilio DOMICILIO = new Domicilio(
            "Av. Los Próceres 1420", "Frente al parque", "Surco", "Lima", "Lima", "15039", "PE",
            null, null);

    private SolicitudRequest persona(TipoDocumento tipo, String documento, LocalDate nacimiento) {
        return new SolicitudRequest(TipoPersona.NATURAL, tipo, documento, "Ana Vega Ríos",
                null, nacimiento, "Importaciones Vega", "987654321", "Componentes de PC",
                "Importamos teclados mecánicos y monitores desde 2019 con almacén propio.",
                DOMICILIO, true, "2026-08");
    }

    private SolicitudRequest empresa(TipoDocumento tipo, String documento, String representante) {
        return new SolicitudRequest(TipoPersona.JURIDICA, tipo, documento, "Importaciones Vega SAC",
                representante, null, "Importaciones Vega", "987654321", "Componentes de PC",
                "Importamos teclados mecánicos y monitores desde 2019 con almacén propio.",
                DOMICILIO, true, "2026-08");
    }

    private LocalDate haceAnios(int anios) {
        return LocalDate.now().minusYears(anios);
    }

    @Nested
    @DisplayName("Persona natural")
    class Natural {

        @Test
        @DisplayName("con DNI válido y mayor de edad pasa")
        void correcta() {
            assertThatCode(() -> persona(TipoDocumento.DNI, "45678912", haceAnios(30)).validarCoherencia())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("el carné de extranjería también vale")
        void carneExtranjeria() {
            assertThatCode(() -> persona(TipoDocumento.CE, "001234567", haceAnios(30)).validarCoherencia())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("no puede identificarse con un RUC")
        void noConRuc() {
            assertThatThrownBy(() -> persona(TipoDocumento.RUC, "20512345678", haceAnios(30)).validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("DNI o carné de extranjería");
        }

        @Test
        @DisplayName("un DNI que no son 8 dígitos se rechaza")
        void dniMalFormado() {
            assertThatThrownBy(() -> persona(TipoDocumento.DNI, "4567", haceAnios(30)).validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("8 dígitos");
        }

        @Test
        @DisplayName("menor de edad no puede vender")
        void menorDeEdad() {
            assertThatThrownBy(() -> persona(TipoDocumento.DNI, "45678912", haceAnios(17)).validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("mayor de 18");
        }

        @Test
        @DisplayName("justo con 18 años recién cumplidos sí")
        void reciénMayor() {
            assertThatCode(() -> persona(TipoDocumento.DNI, "45678912", haceAnios(18)).validarCoherencia())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sin fecha de nacimiento no se puede comprobar la edad")
        void sinFecha() {
            assertThatThrownBy(() -> persona(TipoDocumento.DNI, "45678912", null).validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("fecha de nacimiento");
        }

        @Test
        @DisplayName("no puede declarar representante legal")
        void sinRepresentante() {
            SolicitudRequest conRepresentante = new SolicitudRequest(
                    TipoPersona.NATURAL, TipoDocumento.DNI, "45678912", "Ana Vega Ríos",
                    "Otro Señor", haceAnios(30), "Importaciones Vega", "987654321",
                    "Componentes de PC",
                    "Importamos teclados mecánicos y monitores desde 2019 con almacén propio.",
                    DOMICILIO, true, "2026-08");

            assertThatThrownBy(conRepresentante::validarCoherencia)
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("solo corresponde a una empresa");
        }
    }

    @Nested
    @DisplayName("Empresa")
    class Juridica {

        @Test
        @DisplayName("con RUC y representante pasa")
        void correcta() {
            assertThatCode(() -> empresa(TipoDocumento.RUC, "20512345678", "Ana Vega Ríos").validarCoherencia())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("no puede identificarse con un DNI")
        void noConDni() {
            assertThatThrownBy(() -> empresa(TipoDocumento.DNI, "45678912", "Ana Vega").validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("se identifica con su RUC");
        }

        @Test
        @DisplayName("un RUC que no empieza por 10 ni 20 no lo emite nadie")
        void rucConPrefijoInventado() {
            assertThatThrownBy(() -> empresa(TipoDocumento.RUC, "30512345678", "Ana Vega").validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("empieza por 10 o 20");
        }

        @Test
        @DisplayName("tiene que decir quién firma por ella")
        void sinRepresentante() {
            assertThatThrownBy(() -> empresa(TipoDocumento.RUC, "20512345678", null).validarCoherencia())
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("representante legal");
        }
    }

    @Nested
    @DisplayName("Saneado y valores por defecto")
    class Saneado {

        @Test
        @DisplayName("el país se asume Perú si no viene")
        void paisPorDefecto() {
            Domicilio sinPais = new Domicilio("Av. Siempre Viva 742", null,
                    "Surco", "Lima", "Lima", "15039", null, null, null);

            org.assertj.core.api.Assertions.assertThat(sinPais.pais()).isEqualTo("PE");
        }

        @Test
        @DisplayName("media coordenada se descarta entera: no ubica nada")
        void coordenadaAMedias() {
            // Guardar solo la latitud dejaria un mapa apuntando al meridiano de
            // Greenwich, que es peor que no tener punto: parece un dato.
            Domicilio soloLatitud = new Domicilio("Av. Siempre Viva 742", null,
                    "Surco", "Lima", "Lima", "15039", "PE", new BigDecimal("-12.046374"), null);

            org.assertj.core.api.Assertions.assertThat(soloLatitud.latitud()).isNull();
            org.assertj.core.api.Assertions.assertThat(soloLatitud.longitud()).isNull();
        }

        @Test
        @DisplayName("las dos coordenadas juntas sí se guardan")
        void coordenadasCompletas() {
            Domicilio conPunto = new Domicilio("Av. Siempre Viva 742", null,
                    "Surco", "Lima", "Lima", "15039", "PE",
                    new BigDecimal("-12.046374"), new BigDecimal("-77.042793"));

            org.assertj.core.api.Assertions.assertThat(conPunto.latitud())
                    .isEqualByComparingTo("-12.046374");
            org.assertj.core.api.Assertions.assertThat(conPunto.longitud())
                    .isEqualByComparingTo("-77.042793");
        }

        @Test
        @DisplayName("el documento se pasa a mayúsculas para que un CE con letras cuadre")
        void documentoEnMayusculas() {
            SolicitudRequest conMinusculas = persona(TipoDocumento.CE, "abc123456", haceAnios(30));

            org.assertj.core.api.Assertions.assertThat(conMinusculas.documento()).isEqualTo("ABC123456");
            assertThatCode(conMinusculas::validarCoherencia).doesNotThrowAnyException();
        }
    }
}
