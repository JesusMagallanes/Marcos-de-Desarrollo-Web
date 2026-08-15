package com.backend.usuarios.colaborador;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.backend.usuarios.shared.error.DatosInvalidosException;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Guarda los documentos de identidad fuera de la base y fuera de la web.
 *
 * <p>Aquí se concentra todo lo que puede salir mal con un fichero que sube un
 * desconocido, porque es más fácil revisar un sitio que confiar en que cada
 * sitio que toque ficheros se acuerde de todo.
 *
 * <p>Las tres decisiones que sostienen esto:
 *
 * <ol>
 *   <li><b>El tipo se detecta leyendo el fichero</b>, no creyendo lo que dice el
 *       cliente. Tanto la extensión como la cabecera {@code Content-Type} las
 *       elige quien sube, así que un {@code .jpg} puede traer cualquier cosa.
 *   <li><b>El nombre que llega no se usa nunca para construir la ruta.</b> Se
 *       guarda aparte, solo para enseñárselo al usuario. Un nombre como
 *       {@code ../../application.properties} deja de ser un problema si no se
 *       usa para nada.
 *   <li><b>Nada de esto se sirve como estático.</b> Se lee por un endpoint que
 *       comprueba quién pregunta. Una carpeta de fotos de DNI accesible por URL
 *       es una fuga esperando a que alguien pruebe números.
 * </ol>
 */
@Component
@Slf4j
public class AlmacenDocumentos {

    /** 5 MB. Una foto de un DNI cabe de sobra; un PDF escaneado, también. */
    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path raiz;
    private final MetricasSeguridad metricas;

    public AlmacenDocumentos(@Value("${smartzone.documentos.ruta:/datos/identidad}") String ruta,
            MetricasSeguridad metricas) {
        this.raiz = Path.of(ruta).toAbsolutePath().normalize();
        this.metricas = metricas;
    }

    @PostConstruct
    void prepararCarpeta() throws IOException {
        Files.createDirectories(raiz);
        log.info("Documentos de identidad en {}", raiz);
    }

    /**
     * Lo que se sabe del fichero una vez comprobado.
     *
     * @param ruta relativa a la raíz; es lo que se guarda en la base
     */
    public record Guardado(String ruta, String tipoMime, long tamano, String sha256) {
    }

    /**
     * Comprueba y guarda. Si algo no cuadra, no se escribe nada en disco.
     *
     * <p>El orden importa: primero se valida en memoria y solo al final se
     * escribe. Al revés habría que borrar lo escrito cuando falla la
     * comprobación, y ese borrado es justo el que se olvida.
     */
    public Guardado guardar(MultipartFile fichero) {
        if (fichero == null || fichero.isEmpty()) {
            throw new DatosInvalidosException("No llegó ningún archivo");
        }
        if (fichero.getSize() > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("El archivo supera el máximo permitido de 5 MB");
        }

        byte[] contenido;
        try (InputStream entrada = fichero.getInputStream()) {
            contenido = entrada.readAllBytes();
        } catch (IOException ex) {
            throw new DatosInvalidosException("No se pudo leer el archivo");
        }

        // Se relee el tamaño real. `getSize()` viene de la cabecera que mandó el
        // cliente y puede mentir; esto son los bytes que de verdad llegaron.
        if (contenido.length == 0) {
            throw new DatosInvalidosException("El archivo está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("El archivo supera el máximo permitido de 5 MB");
        }

        FormatoAceptado formato = FormatoAceptado.detectar(contenido).orElseThrow(() -> {
            // Se cuenta AQUÍ y no en el servicio porque este es el único punto
            // que sabe que el contenido no era lo que decía ser. Un rechazo
            // suelto es un usuario confundido; una racha, alguien probando.
            metricas.documento("rechazado");
            return new DatosInvalidosException(
                    "El archivo debe ser una imagen JPG o PNG, o un PDF. "
                            + "Cambiarle la extensión no cambia lo que es.");
        });

        String relativa = "%s/%s%s".formatted(
                LocalDate.now().format(CARPETA), UUID.randomUUID(), formato.extension());

        Path destino = raiz.resolve(relativa).normalize();
        // Cinturón por si algún día la generación de la ruta deja de ser nuestra:
        // si el destino calculado se sale de la raíz, no se escribe.
        if (!destino.startsWith(raiz)) {
            throw new IllegalStateException("Ruta de destino fuera del almacén: " + destino);
        }

        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar el documento", ex);
        }

        return new Guardado(relativa, formato.mime(), contenido.length, sha256(contenido));
    }

    /** Los bytes de un documento ya guardado. */
    public byte[] leer(String rutaRelativa) {
        Path fichero = resolverSeguro(rutaRelativa);
        try {
            return Files.readAllBytes(fichero);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el documento " + rutaRelativa, ex);
        }
    }

    public boolean existe(String rutaRelativa) {
        return Files.exists(resolverSeguro(rutaRelativa));
    }

    /**
     * Borra los bytes. La ficha en la base se conserva: hay que poder demostrar
     * que la verificación se hizo aunque la imagen ya no esté.
     *
     * @return true si había algo que borrar
     */
    public boolean borrar(String rutaRelativa) {
        try {
            return Files.deleteIfExists(resolverSeguro(rutaRelativa));
        } catch (IOException ex) {
            log.warn("No se pudo borrar {}: {}", rutaRelativa, ex.getMessage());
            return false;
        }
    }

    /**
     * Toda ruta que venga de fuera pasa por aquí.
     *
     * <p>Aunque hoy las rutas las generamos nosotros y salen de la base, esto
     * cuesta tres líneas y convierte un futuro descuido en un error controlado
     * en vez de en una lectura de cualquier fichero del contenedor.
     */
    private Path resolverSeguro(String rutaRelativa) {
        Path resuelta = raiz.resolve(rutaRelativa).normalize();
        if (!resuelta.startsWith(raiz)) {
            throw new IllegalStateException("Intento de salir del almacén: " + rutaRelativa);
        }
        return resuelta;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    /**
     * Formatos admitidos, reconocidos por sus primeros bytes.
     *
     * <p>Se aceptan solo tres. Cuantos menos, menos posibilidades de que algo
     * inesperado acabe en disco; y con estos tres se puede mandar cualquier
     * documento: foto del móvil o escaneo.
     */
    private enum FormatoAceptado {

        JPEG("image/jpeg", ".jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }),

        PNG("image/png", ".png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }),

        PDF("application/pdf", ".pdf", new byte[] { '%', 'P', 'D', 'F', '-' });

        private final String mime;
        private final String extension;
        private final byte[] firma;

        FormatoAceptado(String mime, String extension, byte[] firma) {
            this.mime = mime;
            this.extension = extension;
            this.firma = firma;
        }

        String mime() {
            return mime;
        }

        String extension() {
            return extension;
        }

        static java.util.Optional<FormatoAceptado> detectar(byte[] contenido) {
            for (FormatoAceptado formato : values()) {
                if (formato.coincide(contenido)) {
                    return java.util.Optional.of(formato);
                }
            }
            return java.util.Optional.empty();
        }

        private boolean coincide(byte[] contenido) {
            if (contenido.length < firma.length) {
                return false;
            }
            for (int i = 0; i < firma.length; i++) {
                if (contenido[i] != firma[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
