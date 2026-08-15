package com.backend.usuarios.colaborador;

import java.time.Instant;
import java.time.LocalDate;

import com.backend.usuarios.shared.error.ConflictoException;

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
 * Solicitud de un usuario para poder vender en la tienda.
 *
 * <p>Las transiciones se hacen a través de {@link #aprobar(Long)} y
 * {@link #rechazar(Long, String)} y no tocando el estado a mano: así los tres
 * campos que tienen que moverse juntos (estado, quién resolvió y cuándo) no se
 * pueden quedar a medias. La base también lo comprueba con un CHECK, porque un
 * estado incoherente es de los que nadie sabe explicar después.
 */
@Entity
@Table(name = "solicitud_colaborador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudColaborador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quién la envía. Sale del token, nunca del cuerpo de la petición. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /* ── Quién es ── */

    /** Persona o empresa. De aquí cuelgan casi todas las demás reglas. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false, length = 8)
    private TipoPersona tipoPersona;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 3)
    private TipoDocumento tipoDocumento;

    /** Texto, no número: un documento con ceros por delante los perdería. */
    @Column(nullable = false, length = 12)
    private String documento;

    /** Nombre completo del titular, o razón social si es empresa. */
    @Column(name = "nombre_titular", nullable = false, length = 160)
    private String nombreTitular;

    /** Solo empresas. */
    @Column(name = "representante_legal", length = 160)
    private String representanteLegal;

    /** Solo personas. Se guarda la fecha y no la edad, que envejecería mal. */
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    /* ── El negocio ── */

    @Column(name = "nombre_comercial", nullable = false, length = 120)
    private String nombreComercial;

    @Column(name = "telefono_contacto", nullable = false, length = 9)
    private String telefonoContacto;

    @Column(nullable = false, length = 120)
    private String rubro;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    /* ── Dónde ── */

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(length = 200)
    private String referencia;

    @Column(nullable = false, length = 80)
    private String distrito;

    @Column(nullable = false, length = 80)
    private String provincia;

    @Column(nullable = false, length = 80)
    private String departamento;

    @Column(name = "codigo_postal", nullable = false, length = 10)
    private String codigoPostal;

    /** ISO 3166-1 alfa-2. */
    @Column(nullable = false, length = 2)
    private String pais;

    /* ── Condiciones aceptadas ── */

    /**
     * Sin la versión, "aceptó los términos" no dice nada: los términos cambian y
     * después nadie sabe cuáles firmó.
     */
    @Column(name = "terminos_version", nullable = false, length = 20)
    private String terminosVersion;

    @Column(name = "terminos_aceptados_en", nullable = false)
    private Instant terminosAceptadosEn;

    /* ── Estado ── */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoSolicitud estado;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "resuelta_por")
    private Long resueltaPor;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    /* ── Transiciones ── */

    public void aprobar(Long administradorId) {
        exigirQueSePuedaResolver();
        this.estado = EstadoSolicitud.APROBADA;
        this.motivoRechazo = null;
        this.resueltaPor = administradorId;
        this.resueltaEn = Instant.now();
    }

    public void rechazar(Long administradorId, String motivo) {
        exigirQueSePuedaResolver();
        this.estado = EstadoSolicitud.RECHAZADA;
        this.motivoRechazo = motivo;
        this.resueltaPor = administradorId;
        this.resueltaEn = Instant.now();
    }

    /**
     * Se lanza conflicto y no un error de validación: la petición era correcta,
     * lo que pasa es que otro administrador llegó antes. El mensaje dice en qué
     * estado quedó para que la bandeja pueda refrescarse.
     */
    private void exigirQueSePuedaResolver() {
        if (!estado.admiteResolucion()) {
            throw new ConflictoException(
                    "La solicitud ya fue resuelta (%s)".formatted(estado.name().toLowerCase()));
        }
    }
}
