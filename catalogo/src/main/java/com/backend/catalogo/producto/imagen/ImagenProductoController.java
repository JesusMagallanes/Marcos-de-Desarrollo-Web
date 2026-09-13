package com.backend.catalogo.producto.imagen;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backend.catalogo.producto.dto.ProductoDtos.ImagenSubida;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;

/**
 * Fotos de producto alojadas en la propia tienda.
 *
 * <p>Dos puertas. La de subir la usa quien puede publicar —la tienda y los
 * colaboradores— y devuelve una URL relativa que se pega en el formulario como
 * cualquier otra. La de leer es pública y sin comprobar nada: son fotos de la
 * vitrina, lo mismo que se ve al navegar.
 *
 * <p>La URL lleva la fecha y un UUID, y no cambia nunca para el mismo fichero.
 * Eso es lo que permite decirle al navegador que la guarde un año: reemplazar
 * una foto es subir otra y cambiar la URL del producto, no pisar la anterior.
 */
@RestController
@RequestMapping(ImagenProductoController.RUTA)
@RequiredArgsConstructor
public class ImagenProductoController {

    public static final String RUTA = "/api/productos/imagenes";

    private final AlmacenImagenes almacen;

    /**
     * Sube una foto y devuelve dónde quedó.
     *
     * <p>Cualquiera que pueda publicar productos puede subir fotos; qué producto
     * la usa se decide después, al guardar el formulario. Una foto subida y
     * nunca usada la retira {@link PurgaImagenes}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERMISO_PRODUCTOS_GESTIONAR', 'PERMISO_PRODUCTOS_PROPIOS')")
    public ImagenSubida subir(@RequestPart("archivo") MultipartFile archivo) {
        AlmacenImagenes.Guardada guardada = almacen.guardar(archivo);
        return new ImagenSubida(RUTA + "/" + guardada.ruta(), guardada.tipoMime(),
                guardada.tamano());
    }

    /**
     * Sirve una foto. Público.
     *
     * <p>Las tres variables se validan juntas contra el patrón del almacén: lo
     * que no tenga exactamente esa forma es un 404 antes de mirar el disco.
     * Un 404 y no un 400, porque para quien prueba rutas la diferencia entre
     * «mal formada» y «no existe» es información que no necesita.
     */
    @GetMapping("/{anio}/{mes}/{nombre}")
    public ResponseEntity<byte[]> ver(@PathVariable String anio, @PathVariable String mes,
            @PathVariable String nombre) {

        AlmacenImagenes.Imagen imagen = almacen.leer(anio + "/" + mes + "/" + nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen no encontrada"));

        return ResponseEntity.ok()
                // El nombre no se reutiliza jamás, así que la respuesta puede
                // guardarse para siempre; Spring Security respeta este valor y
                // no pone su `no-store`.
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(imagen.tipoMime()))
                .body(imagen.contenido());
    }
}
