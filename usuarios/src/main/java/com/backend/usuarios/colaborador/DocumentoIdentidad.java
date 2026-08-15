package com.backend.usuarios.colaborador;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ficha de un documento subido. Los bytes no están aquí: viven en disco y esto
 * guarda dónde y qué se comprobó de ellos.
 *
 * <p>Nace sin solicitud ({@code solicitudId} nulo) porque el formulario sube
 * cada archivo según se elige, antes de darle a enviar. Al enviar, la solicitud
 * lo reclama con {@link #asignarA(Long)}.
 */
@Entity
@Table(name = "documento_identidad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentoIdentidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quién lo subió. Junto al administrador, el único que puede leerlo. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Nulo mientras nadie lo haya enviado con una solicitud. */
    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAdjunto tipo;

    /**
     * El nombre con el que llegó. Solo para enseñarlo. Nunca se usa para
     * construir rutas: es texto que eligió quien subió el archivo.
     */
    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    /** El detectado por los primeros bytes, no el que declaró el cliente. */
    @Column(name = "tipo_mime", nullable = false, length = 50)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    /** Ruta relativa dentro del almacén. Generada, opaca. */
    @Column(nullable = false, length = 255)
    private String ruta;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn;

    /** Cuándo se borraron los bytes. La ficha sobrevive como traza. */
    @Column(name = "purgado_en")
    private Instant purgadoEn;

    /* ── Estado ── */

    /** Ya no quedan bytes que leer, solo el registro de que existieron. */
    public boolean estaPurgado() {
        return purgadoEn != null;
    }

    /**
     * Lo reclama una solicitud al enviarse.
     *
     * <p>Se impide reasignarlo: un adjunto ya enviado con una solicitud es parte
     * de esa revisión, y moverlo a otra dejaría a la primera sin la prueba con
     * la que se aprobó.
     */
    public void asignarA(Long solicitudId) {
        if (this.solicitudId != null && !this.solicitudId.equals(solicitudId)) {
            throw new IllegalStateException(
                    "El documento %d ya pertenece a la solicitud %d".formatted(id, this.solicitudId));
        }
        this.solicitudId = solicitudId;
    }

    public void marcarPurgado() {
        this.purgadoEn = Instant.now();
    }
}
