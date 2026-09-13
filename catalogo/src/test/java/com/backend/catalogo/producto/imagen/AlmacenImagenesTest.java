package com.backend.catalogo.producto.imagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.backend.catalogo.shared.error.DatosInvalidosException;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.shared.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * El almacén de fotos de producto.
 *
 * <p>Lo que se prueba es lo que sostiene la seguridad: que el tipo se decide
 * leyendo el contenido, que la ruta pública solo admite la forma que nosotros
 * generamos, y que nada de lo que venga de fuera puede salir de la carpeta.
 */
@DisplayName("Almacén de imágenes de producto")
class AlmacenImagenesTest {

    @TempDir
    Path carpeta;

    private AlmacenImagenes almacen;

    /** Cabeceras reales de cada formato, que es lo que se detecta. */
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1 };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 };
    private static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };

    @BeforeEach
    void prepararAlmacen() throws IOException {
        MetricasSeguridad metricas =
                new MetricasSeguridad(new SimpleMeterRegistry(), new LimitadorPeticiones());
        almacen = new AlmacenImagenes(carpeta.toString(), metricas);
        almacen.prepararCarpeta();
    }

    private static MockMultipartFile fichero(String nombre, String tipoDeclarado, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipoDeclarado, contenido);
    }

    @Nested
    @DisplayName("Qué acepta")
    class Acepta {

        @Test
        @DisplayName("JPEG, PNG y WebP, con el tipo detectado y no el declarado")
        void losTresFormatos() {
            // El Content-Type declarado miente en los tres: da igual.
            assertThat(almacen.guardar(fichero("a.bin", "text/plain", JPEG)).tipoMime())
                    .isEqualTo("image/jpeg");
            assertThat(almacen.guardar(fichero("b.bin", "text/plain", PNG)).tipoMime())
                    .isEqualTo("image/png");
            assertThat(almacen.guardar(fichero("c.bin", "text/plain", WEBP)).tipoMime())
                    .isEqualTo("image/webp");
        }

        @Test
        @DisplayName("la ruta que devuelve tiene la forma pública y se puede releer")
        void rutaPublicaYLectura() {
            var guardada = almacen.guardar(fichero("foto.jpg", "image/jpeg", JPEG));

            assertThat(guardada.ruta()).matches(AlmacenImagenes.RUTA_VALIDA);
            assertThat(guardada.ruta()).endsWith(".jpg");
            assertThat(almacen.leer(guardada.ruta())).isPresent().get().satisfies(imagen -> {
                assertThat(imagen.contenido()).isEqualTo(JPEG);
                assertThat(imagen.tipoMime()).isEqualTo("image/jpeg");
            });
        }

        @Test
        @DisplayName("el nombre original no participa en la ruta")
        void nombreOriginalIgnorado() {
            var guardada = almacen.guardar(
                    fichero("../../application.properties", "image/png", PNG));

            assertThat(guardada.ruta()).doesNotContain("..").doesNotContain("application");
            assertThat(carpeta.resolve(guardada.ruta())).exists();
        }
    }

    @Nested
    @DisplayName("Qué rechaza")
    class Rechaza {

        @Test
        @DisplayName("un ejecutable disfrazado de JPG")
        void extensionMentirosa() {
            byte[] noEsImagen = "MZ ejecutable".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> almacen.guardar(fichero("foto.jpg", "image/jpeg", noEsImagen)))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("Cambiarle la extensión no cambia lo que es");
            assertThat(ficherosEnDisco()).isZero();
        }

        @Test
        @DisplayName("un SVG, aunque sea una imagen: puede llevar scripts")
        void svg() {
            byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> almacen.guardar(fichero("logo.svg", "image/svg+xml", svg)))
                    .isInstanceOf(DatosInvalidosException.class);
        }

        @Test
        @DisplayName("un RIFF que no es WebP (un WAV, por ejemplo)")
        void riffQueNoEsWebp() {
            byte[] wav = { 'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ' };

            assertThatThrownBy(() -> almacen.guardar(fichero("a.webp", "image/webp", wav)))
                    .isInstanceOf(DatosInvalidosException.class);
        }

        @Test
        @DisplayName("vacío y demasiado grande")
        void vacioYGrande() {
            assertThatThrownBy(() -> almacen.guardar(fichero("a.jpg", "image/jpeg", new byte[0])))
                    .isInstanceOf(DatosInvalidosException.class);

            byte[] enorme = new byte[(int) AlmacenImagenes.TAMANO_MAXIMO + 1];
            System.arraycopy(JPEG, 0, enorme, 0, JPEG.length);
            assertThatThrownBy(() -> almacen.guardar(fichero("a.jpg", "image/jpeg", enorme)))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("5 MB");
        }
    }

    @Nested
    @DisplayName("Qué se puede leer")
    class Lectura {

        @Test
        @DisplayName("solo rutas con la forma exacta que generamos")
        void formaExacta() throws IOException {
            // Un fichero real fuera de la carpeta del almacén, que es lo que un
            // `..` intentaría alcanzar.
            Path fuera = Files.writeString(carpeta.resolveSibling("secreto.txt"), "no");

            assertThat(almacen.leer("../secreto.txt")).isEmpty();
            assertThat(almacen.leer("2026/09/secreto.txt")).isEmpty();
            assertThat(almacen.leer("2026/09/00000000-0000-0000-0000-000000000000.svg")).isEmpty();
            assertThat(almacen.leer(null)).isEmpty();
            assertThat(fuera).exists();
        }

        @Test
        @DisplayName("una ruta bien formada que no existe es simplemente vacío")
        void noExiste() {
            assertThat(almacen.leer("2026/09/00000000-0000-0000-0000-000000000000.jpg")).isEmpty();
        }

        @Test
        @DisplayName("borrar deja de servirla")
        void borrar() {
            var guardada = almacen.guardar(fichero("a.png", "image/png", PNG));

            assertThat(almacen.borrar(guardada.ruta())).isTrue();
            assertThat(almacen.leer(guardada.ruta())).isEmpty();
            assertThat(almacen.borrar(guardada.ruta())).isFalse();
        }
    }

    private long ficherosEnDisco() {
        try (var s = Files.walk(carpeta)) {
            return s.filter(Files::isRegularFile).count();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
