package com.backend.catalogo.producto.imagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.backend.catalogo.producto.ProductoImagenRepository;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.shared.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * La purga borra lo huérfano y viejo, y SOLO eso.
 *
 * <p>Tres casos que tienen que convivir: una foto en uso por un producto no se
 * toca aunque sea vieja; una huérfana reciente tampoco, porque puede ser la de
 * un formulario a medio rellenar; y una huérfana vieja se va.
 */
@DisplayName("Purga de imágenes huérfanas")
class PurgaImagenesTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 };

    @TempDir
    Path carpeta;

    private AlmacenImagenes almacen;
    private ProductoRepository productos;
    private ProductoImagenRepository galerias;
    private PurgaImagenes purga;

    @BeforeEach
    void preparar() throws IOException {
        almacen = new AlmacenImagenes(carpeta.toString(),
                new MetricasSeguridad(new SimpleMeterRegistry(), new LimitadorPeticiones()));
        almacen.prepararCarpeta();
        productos = mock(ProductoRepository.class);
        galerias = mock(ProductoImagenRepository.class);
        purga = new PurgaImagenes(almacen, productos, galerias);
    }

    @Test
    @DisplayName("borra la huérfana vieja y respeta la usada y la reciente")
    void soloHuerfanasViejas() throws IOException {
        String usada = subir();
        String huerfanaVieja = subir();
        String huerfanaReciente = subir();

        Instant haceDiezDias = Instant.now().minus(10, ChronoUnit.DAYS);
        envejecer(usada, haceDiezDias);
        envejecer(huerfanaVieja, haceDiezDias);

        // Da igual por cuál de las dos vías esté referenciada: principal o galería.
        when(productos.imagenesPrincipalesQueEmpiezanPor(anyString())).thenReturn(List.of());
        when(galerias.urlsQueEmpiezanPor(anyString()))
                .thenReturn(List.of(ImagenProductoController.RUTA + "/" + usada));

        int borradas = purga.purgarHuerfanas(Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(borradas).isEqualTo(1);
        assertThat(almacen.leer(usada)).isPresent();
        assertThat(almacen.leer(huerfanaReciente)).isPresent();
        assertThat(almacen.leer(huerfanaVieja)).isEmpty();
    }

    @Test
    @DisplayName("no toca ficheros que no tengan la forma del almacén")
    void ignoraLoAjeno() throws IOException {
        Path ajeno = carpeta.resolve("notas.txt");
        Files.writeString(ajeno, "no es una foto");
        Files.setLastModifiedTime(ajeno,
                FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));
        when(productos.imagenesPrincipalesQueEmpiezanPor(anyString())).thenReturn(List.of());
        when(galerias.urlsQueEmpiezanPor(anyString())).thenReturn(List.of());

        assertThat(purga.purgarHuerfanas(Instant.now())).isZero();
        assertThat(ajeno).exists();
    }

    private String subir() {
        return almacen.guardar(new MockMultipartFile("archivo", "a.png", "image/png", PNG)).ruta();
    }

    private void envejecer(String ruta, Instant cuando) throws IOException {
        Files.setLastModifiedTime(carpeta.resolve(ruta), FileTime.from(cuando));
    }
}
