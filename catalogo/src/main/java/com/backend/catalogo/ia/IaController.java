package com.backend.catalogo.ia;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backend.catalogo.ia.dto.IaDtos.AnalisisModeracion;
import com.backend.catalogo.ia.dto.IaDtos.AyudaResena;
import com.backend.catalogo.ia.dto.IaDtos.AyudaResenaRequest;
import com.backend.catalogo.ia.dto.IaDtos.BusquedaInterpretada;
import com.backend.catalogo.ia.dto.IaDtos.BusquedaPorFoto;
import com.backend.catalogo.ia.dto.IaDtos.BusquedaRequest;
import com.backend.catalogo.ia.dto.IaDtos.Comparacion;
import com.backend.catalogo.ia.dto.IaDtos.ComparacionRequest;
import com.backend.catalogo.ia.dto.IaDtos.Complementos;
import com.backend.catalogo.ia.dto.IaDtos.ComplementosRequest;
import com.backend.catalogo.ia.dto.IaDtos.ComoCorregir;
import com.backend.catalogo.ia.dto.IaDtos.EstadoIa;
import com.backend.catalogo.ia.dto.IaDtos.PreguntaRequest;
import com.backend.catalogo.ia.dto.IaDtos.RedaccionRequest;
import com.backend.catalogo.ia.dto.IaDtos.RedaccionSugerida;
import com.backend.catalogo.ia.dto.IaDtos.RespuestaFicha;
import com.backend.catalogo.ia.dto.IaDtos.ResumenResenas;
import com.backend.catalogo.ia.dto.IaDtos.RevisionFoto;
import com.backend.catalogo.ia.dto.IaDtos.RevisionFotoRequest;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.seguridad.JwtUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Las funciones de IA aplicada del catálogo.
 *
 * <p>Todas responden {@code 204 No Content} cuando la IA no puede aportar —sin
 * modelo configurado, texto sospechoso, proveedor caído—, no un error. Para el
 * frontend «no hay sugerencia» es un estado normal: sigue con lo que tenía.
 * Las excepciones son las que tienen una versión sin IA útil por sí sola: la
 * moderación (reglas) y el comparador (la tabla), que responden siempre.
 *
 * <p>El chat no está aquí: usa la IA por dentro de {@code /api/chatbot}, y el
 * «¿por qué veo esto?» vive en {@code /api/descubrimiento}, donde se resuelve el
 * sujeto.
 */
@RestController
@RequestMapping("/api/ia")
@Validated
@RequiredArgsConstructor
public class IaController {

    private final ClienteIa ia;
    private final InterpreteBusqueda interprete;
    private final RedactorFichas redactor;
    private final RevisorModeracion revisor;
    private final ResumenResenasService resumenes;
    private final AsistenteProducto asistenteProducto;
    private final Comparador comparador;
    private final AsistenteCarrito asistenteCarrito;
    private final AyudaResenas ayudaResenas;
    private final AsistenteColaborador asistenteColaborador;
    private final BusquedaPorFotoService busquedaPorFoto;

    /* ══════════════ Públicas ══════════════ */

    /** Decide si el frontend enseña los botones de IA. */
    @GetMapping("/estado")
    public EstadoIa estado() {
        return new EstadoIa(ia.disponible());
    }

    /** Una búsqueda escrita como se habla, convertida en filtros reales. */
    @PostMapping("/busqueda/interpretar")
    public ResponseEntity<BusquedaInterpretada> interpretar(@Valid @RequestBody BusquedaRequest dto) {
        // No `ResponseEntity.of`: con un Optional vacío responde 404, y «sin
        // sugerencia» no es «no existe».
        return conContenido(interprete.interpretar(dto.consulta())
                .map(InterpreteBusqueda.Interpretacion::busqueda).orElse(null));
    }

    /** Buscar con una foto. La foto no se guarda. */
    @PostMapping(value = "/busqueda/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BusquedaPorFoto> buscarPorFoto(@RequestPart("archivo") MultipartFile archivo) {
        return conContenido(busquedaPorFoto.buscar(archivo).orElse(null));
    }

    /** Resumen de las reseñas aprobadas de un producto. */
    @GetMapping("/productos/{id}/resumen-resenas")
    public ResponseEntity<ResumenResenas> resumenResenas(@PathVariable @Positive Long id) {
        return conContenido(resumenes.resumir(id).orElse(null));
    }

    /** Una pregunta sobre un producto, contestada con su ficha. */
    @PostMapping("/productos/{id}/preguntar")
    public ResponseEntity<RespuestaFicha> preguntar(@PathVariable @Positive Long id,
            @Valid @RequestBody PreguntaRequest dto) {
        return conContenido(asistenteProducto.preguntar(id, dto.pregunta()).orElse(null));
    }

    /** Comparación de 2 o 3 productos. Siempre responde: la tabla no necesita IA. */
    @PostMapping("/comparar")
    public Comparacion comparar(@Valid @RequestBody ComparacionRequest dto) {
        return comparador.comparar(dto);
    }

    /** Accesorios que completan el carrito. Recibe ids, no el carrito: no hace falta sesión. */
    @PostMapping("/carrito/complementos")
    public ResponseEntity<Complementos> complementos(@Valid @RequestBody ComplementosRequest dto) {
        return conContenido(asistenteCarrito.complementos(dto.productoIds())
                .filter(c -> !c.productos().isEmpty()).orElse(null));
    }

    /* ══════════════ Con sesión ══════════════ */

    /** Preguntas para escribir una reseña mejor; nunca el texto. */
    @PostMapping("/resenas/ayuda")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AyudaResena> ayudaResena(@Valid @RequestBody AyudaResenaRequest dto) {
        return conContenido(ayudaResenas.sugerir(dto).orElse(null));
    }

    /** Borrador de ficha, para quien puede publicar. */
    @PostMapping("/productos/redactar")
    @PreAuthorize("hasAnyAuthority('PERMISO_PRODUCTOS_GESTIONAR', 'PERMISO_PRODUCTOS_PROPIOS')")
    public ResponseEntity<RedaccionSugerida> redactar(@Valid @RequestBody RedaccionRequest dto) {
        return conContenido(redactor.redactar(dto).orElse(null));
    }

    /** Revisión de una foto ya subida a la tienda. */
    @PostMapping("/productos/revisar-foto")
    @PreAuthorize("hasAnyAuthority('PERMISO_PRODUCTOS_GESTIONAR', 'PERMISO_PRODUCTOS_PROPIOS')")
    public ResponseEntity<RevisionFoto> revisarFoto(@Valid @RequestBody RevisionFotoRequest dto) {
        return conContenido(asistenteColaborador.revisarFoto(dto.url(), dto.nombre()).orElse(null));
    }

    /** Pasos para corregir un producto propio rechazado. */
    @GetMapping("/productos/mios/{id}/como-corregir")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_PROPIOS')")
    public ResponseEntity<ComoCorregir> comoCorregir(@PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return conContenido(asistenteColaborador.comoCorregir(id, JwtUtils.uidDe(jwt)).orElse(null));
    }

    /** Análisis previo de un producto en moderación. Siempre responde: las reglas corren sin IA. */
    @GetMapping("/moderacion/productos/{id}")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public AnalisisModeracion analizarProducto(@PathVariable @Positive Long id) {
        return revisor.producto(id);
    }

    /** Análisis previo de una reseña en moderación. */
    @GetMapping("/moderacion/valoraciones/{id}")
    @PreAuthorize("hasAuthority('PERMISO_VALORACIONES_GESTIONAR')")
    public AnalisisModeracion analizarValoracion(@PathVariable @Positive Long id) {
        return revisor.valoracion(id);
    }

    private static <T> ResponseEntity<T> conContenido(T cuerpo) {
        return cuerpo == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(cuerpo);
    }
}
