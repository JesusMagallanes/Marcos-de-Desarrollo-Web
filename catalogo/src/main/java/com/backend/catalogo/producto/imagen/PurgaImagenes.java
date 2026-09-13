package com.backend.catalogo.producto.imagen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backend.catalogo.producto.ProductoImagenRepository;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.shared.seguridad.ContextoRls;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Retira las fotos que se subieron y ningún producto llegó a usar.
 *
 * <p>Subir va antes que guardar el formulario, así que siempre habrá fotos
 * huérfanas: la de quien cambió de idea, la que se subió dos veces, la del
 * formulario que se cerró sin guardar. Sin esto el volumen solo crece.
 *
 * <p>Se borra lo que lleva más de unos días en disco y no aparece ni como
 * imagen principal ni en ninguna galería. El plazo de gracia existe porque la
 * foto se sube ANTES de que exista el producto que la referencia: purgar al
 * instante borraría la foto de un formulario a medio rellenar.
 *
 * <p>Corre como sistema. Con RLS activo, una tarea sin usuario solo vería los
 * productos aprobados; los pendientes de un colaborador quedarían fuera y sus
 * fotos parecerían huérfanas. Es exactamente el caso que la marca de sistema
 * existe para cubrir, y por eso se pone en el método programado, antes de que
 * la transacción tome la conexión.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurgaImagenes {

    private final AlmacenImagenes almacen;
    private final ProductoRepository productos;
    private final ProductoImagenRepository galerias;

    @Value("${smartzone.imagenes.dias-huerfanas:7}")
    private int diasHuerfanas;

    /** De madrugada, como la purga de documentos de identidad. */
    @Scheduled(cron = "${smartzone.imagenes.cron-purga:0 45 3 * * *}")
    public void purgar() {
        ContextoRls.comoSistema(() -> {
            int borradas = purgarHuerfanas(Instant.now().minus(diasHuerfanas, ChronoUnit.DAYS));
            if (borradas > 0) {
                log.info("Purga de imágenes de producto: {} huérfanas borradas", borradas);
            }
        });
    }

    /**
     * Borra los ficheros anteriores al corte que ningún producto referencia.
     *
     * <p>Sin {@code @Transactional} a propósito: llamado desde {@link #purgar}
     * no atravesaría el proxy y la anotación no haría nada. Cada consulta del
     * repositorio abre la suya, que es lo único que hace falta aquí.
     *
     * @return cuántos se borraron
     */
    public int purgarHuerfanas(Instant corte) {
        Set<String> enUso = new HashSet<>();
        String prefijo = ImagenProductoController.RUTA + "/";
        productos.imagenesPrincipalesQueEmpiezanPor(prefijo)
                .forEach(url -> enUso.add(url.substring(prefijo.length())));
        galerias.urlsQueEmpiezanPor(prefijo)
                .forEach(url -> enUso.add(url.substring(prefijo.length())));

        Path raiz = almacen.raiz();
        if (!Files.isDirectory(raiz)) {
            return 0;
        }
        int borradas = 0;
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            for (Path fichero : (Iterable<Path>) ficheros.filter(Files::isRegularFile)::iterator) {
                String relativa = raiz.relativize(fichero).toString().replace('\\', '/');
                if (!AlmacenImagenes.RUTA_VALIDA.matcher(relativa).matches()
                        || enUso.contains(relativa)
                        || !esAnteriorA(fichero, corte)) {
                    continue;
                }
                if (almacen.borrar(relativa)) {
                    borradas++;
                }
            }
        } catch (IOException ex) {
            log.warn("No se pudo recorrer el almacén de imágenes: {}", ex.getMessage());
        }
        return borradas;
    }

    private static boolean esAnteriorA(Path fichero, Instant corte) {
        try {
            return Files.getLastModifiedTime(fichero).toInstant().isBefore(corte);
        } catch (IOException ex) {
            return false;
        }
    }
}
