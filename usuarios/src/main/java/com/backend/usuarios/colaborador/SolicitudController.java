package com.backend.usuarios.colaborador;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.backend.usuarios.colaborador.dto.SolicitudDtos.RechazoRequest;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudAdminResponse;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudRequest;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudResponse;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SubidaResponse;
import com.backend.usuarios.shared.security.UsuarioAutenticado;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Solicitudes para vender en la tienda.
 *
 * <p>El solicitante siempre sale del token: no hay ningún endpoint que acepte un
 * {@code usuarioId} por la URL o por el cuerpo, para que nadie pueda enviar una
 * solicitud a nombre de otro ni consultar la ajena cambiando un número.
 *
 * <p>Contrato completo en {@code docs/contrato-colaboradores.md}.
 */
@RestController
@RequestMapping("/api/colaboradores/solicitudes")
@Validated
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService servicio;

    /* ── Adjuntos de identidad ── */

    /**
     * Sube un documento, antes de enviar la solicitud.
     *
     * <p>Va aparte y no dentro del formulario porque así el usuario puede
     * subirlos según los elige, ver que se cargaron y corregir el que salió
     * borroso. Si viajaran con el resto, un fallo de validación en cualquier
     * campo de texto le obligaría a volver a mandar varios megas de fotos.
     */
    @PostMapping("/adjuntos")
    public ResponseEntity<SubidaResponse> subirAdjunto(@RequestParam TipoAdjunto tipo,
            @RequestParam("archivo") MultipartFile archivo,
            UsuarioAutenticado autenticado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.subir(autenticado.id(), tipo, archivo));
    }

    /**
     * Devuelve el archivo. Solo su dueño y el administrador.
     *
     * <p>Se manda {@code Content-Disposition: attachment} y
     * {@code X-Content-Type-Options: nosniff} para que el navegador lo descargue
     * en vez de intentar interpretarlo: un PDF que se abre dentro del dominio
     * puede ejecutar cosas, y estos ficheros vienen de fuera.
     *
     * <p>{@code no-store} porque una foto de un DNI no debe quedarse en la caché
     * del navegador ni en ninguna intermedia.
     */
    @GetMapping("/adjuntos/{id}")
    public ResponseEntity<byte[]> descargarAdjunto(@PathVariable @Positive Long id,
            UsuarioAutenticado autenticado) {
        SolicitudService.Descarga descarga =
                servicio.descargar(id, autenticado.id(), autenticado.esAdmin());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, descarga.tipoMime())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombre(), StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff")
                .body(descarga.contenido());
    }

    /* ── Solicitante ── */

    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody SolicitudRequest dto,
            UsuarioAutenticado autenticado) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicio.crear(autenticado.id(), dto));
    }

    /**
     * La última solicitud del usuario en curso.
     *
     * <p>204 y no 404 cuando no hay ninguna: no haber solicitado nunca es un
     * estado normal, no un error, y el cliente no debería pintarlo como tal.
     */
    @GetMapping("/mia")
    public ResponseEntity<SolicitudResponse> mia(UsuarioAutenticado autenticado) {
        return servicio.mia(autenticado.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /* ── Administrador ── */

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<SolicitudAdminResponse> listar(
            @RequestParam(required = false) EstadoSolicitud estado) {
        return servicio.listar(estado);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SolicitudAdminResponse aprobar(@PathVariable @Positive Long id,
            UsuarioAutenticado autenticado) {
        return servicio.aprobar(id, autenticado.id());
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public SolicitudAdminResponse rechazar(@PathVariable @Positive Long id,
            @Valid @RequestBody RechazoRequest dto,
            UsuarioAutenticado autenticado) {
        return servicio.rechazar(id, autenticado.id(), dto);
    }
}
