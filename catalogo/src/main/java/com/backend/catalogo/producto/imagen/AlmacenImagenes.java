package com.backend.catalogo.producto.imagen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.backend.catalogo.shared.error.DatosInvalidosException;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Guarda las fotos de producto en disco propio, para servirlas desde aquí.
 *
 * <p>Existe porque el panel solo admitía URLs de otros sitios, y eso es
 * exactamente lo que se rompe: el origen bloquea el enlace, borra la foto, o
 * el service worker no puede traerla y la tarjeta enseña el marcador. Una foto
 * que vive en este mismo origen no depende de nadie.
 *
 * <p>Es el mismo esquema que {@code AlmacenDocumentos} en {@code usuarios}, con
 * las mismas tres reglas: el tipo se decide leyendo los primeros bytes, el
 * nombre que sube el cliente no construye la ruta, y toda ruta que venga de
 * fuera se comprueba contra la raíz. Lo que cambia es el uso: estas fotos son
 * públicas por definición —son la vitrina— y por eso se sirven sin comprobar
 * quién pregunta, con caché larga y nombre que no se repite.
 */
@Component
@Slf4j
public class AlmacenImagenes {

    /** 5 MB. Una foto de producto para la tienda no necesita más. */
    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    /**
     * Forma de una ruta relativa válida: {@code 2026/09/<uuid>.webp}.
     *
     * <p>Es lo único que el endpoint público acepta. Cualquier otra cosa —un
     * {@code ..}, una barra de más, otra extensión— es un 404 sin llegar a
     * tocar el disco.
     */
    public static final Pattern RUTA_VALIDA = Pattern.compile(
            "^\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path raiz;
    private final MetricasSeguridad metricas;

    public AlmacenImagenes(@Value("${smartzone.imagenes.ruta:/datos/imagenes}") String ruta,
            MetricasSeguridad metricas) {
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
        this.metricas = metricas;
    }

    @PostConstruct
    void prepararCarpeta() throws IOException {
        Files.createDirectories(raiz);
        log.info("Imágenes de producto en {}", raiz);
    }

    /** Lo guardado: la ruta relativa es lo que viaja en la URL pública. */
    public record Guardada(String ruta, String tipoMime, long tamano) {
    }

    /** Una imagen leída del disco, lista para servirse. */
    public record Imagen(byte[] contenido, String tipoMime) {
    }

    /**
     * Comprueba y guarda. Si algo no cuadra, no se escribe nada en disco.
     *
     * <p>Primero se valida en memoria y solo al final se escribe: al revés
     * habría que borrar lo escrito cuando falla la comprobación, y ese borrado
     * es justo el que se olvida.
     */
    public Guardada guardar(MultipartFile fichero) {
        if (fichero == null || fichero.isEmpty()) {
            throw new DatosInvalidosException("No llegó ningún archivo");
        }
        if (fichero.getSize() > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("La imagen supera el máximo permitido de 5 MB");
        }

        byte[] contenido;
        try (InputStream entrada = fichero.getInputStream()) {
            contenido = entrada.readAllBytes();
        } catch (IOException ex) {
            throw new DatosInvalidosException("No se pudo leer el archivo");
        }

        // `getSize()` viene de la cabecera que mandó el cliente; esto son los
        // bytes que de verdad llegaron.
        if (contenido.length == 0) {
            throw new DatosInvalidosException("El archivo está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("La imagen supera el máximo permitido de 5 MB");
        }

        Formato formato = Formato.detectar(contenido).orElseThrow(() -> {
            // Un rechazo suelto es un usuario confundido; una racha, alguien
            // probando qué cuela.
            metricas.entradaRechazada("imagen");
            return new DatosInvalidosException(
                    "La imagen debe ser JPG, PNG o WebP. Cambiarle la extensión no cambia lo que es.");
        });

        String relativa = "%s/%s%s".formatted(
                LocalDate.now().format(CARPETA), UUID.randomUUID(), formato.extension());

        Path destino = raiz.resolve(relativa).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalStateException("Ruta de destino fuera del almacén: " + destino);
        }

        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen", ex);
        }

        return new Guardada(relativa, formato.mime(), contenido.length);
    }

    /**
     * Una imagen ya guardada, o vacío si no existe.
     *
     * <p>Vacío y no excepción: para el endpoint público «no existe» es la
     * respuesta normal a cualquier ruta inventada, no un fallo.
     */
    public Optional<Imagen> leer(String rutaRelativa) {
        if (rutaRelativa == null || !RUTA_VALIDA.matcher(rutaRelativa).matches()) {
            return Optional.empty();
        }
        Path fichero = resolverSeguro(rutaRelativa);
        if (!Files.isRegularFile(fichero)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Imagen(Files.readAllBytes(fichero),
                    Formato.porExtension(rutaRelativa).mime()));
        } catch (IOException ex) {
            log.warn("No se pudo leer la imagen {}: {}", rutaRelativa, ex.getMessage());
            return Optional.empty();
        }
    }

    /** @return true si había algo que borrar */
    public boolean borrar(String rutaRelativa) {
        try {
            return Files.deleteIfExists(resolverSeguro(rutaRelativa));
        } catch (IOException ex) {
            log.warn("No se pudo borrar {}: {}", rutaRelativa, ex.getMessage());
            return false;
        }
    }

    /** La raíz del almacén, para que la purga pueda recorrerlo. */
    public Path raiz() {
        return raiz;
    }

    /**
     * Toda ruta que venga de fuera pasa por aquí.
     *
     * <p>Hoy las rutas las generamos nosotros, pero esto cuesta tres líneas y
     * convierte un futuro descuido en un error controlado en vez de en una
     * lectura de cualquier fichero del contenedor.
     */
    private Path resolverSeguro(String rutaRelativa) {
        Path resuelta = raiz.resolve(rutaRelativa).normalize();
        if (!resuelta.startsWith(raiz)) {
            throw new IllegalStateException("Intento de salir del almacén: " + rutaRelativa);
        }
        return resuelta;
    }

    /**
     * Formatos admitidos, reconocidos por sus primeros bytes.
     *
     * <p>Solo los tres que un navegador pinta sin ayuda y no llevan código
     * dentro. Un SVG se rechaza aunque sea una imagen: puede traer scripts.
     */
    enum Formato {
        JPEG("image/jpeg", ".jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }),

        PNG("image/png", ".png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }),

        /** RIFF, cuatro bytes de tamaño, y WEBP. Los del tamaño se saltan. */
        WEBP("image/webp", ".webp", new byte[] { 'R', 'I', 'F', 'F' }, new byte[] { 'W', 'E', 'B', 'P' });

        private final String mime;
        private final String extension;
        private final byte[] firma;
        private final byte[] firmaEnOcho;

        Formato(String mime, String extension, byte[] firma) {
            this(mime, extension, firma, new byte[0]);
        }

        Formato(String mime, String extension, byte[] firma, byte[] firmaEnOcho) {
            this.mime = mime;
            this.extension = extension;
            this.firma = firma;
            this.firmaEnOcho = firmaEnOcho;
        }

        String mime() {
            return mime;
        }

        String extension() {
            return extension;
        }

        static Optional<Formato> detectar(byte[] contenido) {
            return Arrays.stream(values()).filter(f -> f.coincide(contenido)).findFirst();
        }

        static Formato porExtension(String ruta) {
            return Arrays.stream(values())
                    .filter(f -> ruta.endsWith(f.extension))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Extensión desconocida: " + ruta));
        }

        private boolean coincide(byte[] contenido) {
            return empiezaPor(contenido, 0, firma) && empiezaPor(contenido, 8, firmaEnOcho);
        }

        private static boolean empiezaPor(byte[] contenido, int desde, byte[] esperado) {
            if (esperado.length == 0) {
                return true;
            }
            if (contenido.length < desde + esperado.length) {
                return false;
            }
            for (int i = 0; i < esperado.length; i++) {
                if (contenido[desde + i] != esperado[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
