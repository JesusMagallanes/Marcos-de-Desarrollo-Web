package com.backend.usuarios.colaborador.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import com.backend.usuarios.colaborador.DocumentoIdentidad;
import com.backend.usuarios.colaborador.EstadoSolicitud;
import com.backend.usuarios.colaborador.SolicitudColaborador;
import com.backend.usuarios.colaborador.TipoAdjunto;
import com.backend.usuarios.colaborador.TipoDocumento;
import com.backend.usuarios.colaborador.TipoPersona;
import com.backend.usuarios.shared.error.DatosInvalidosException;
import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SolicitudDtos {

    private SolicitudDtos() {
    }

    /** Edad mínima para obligarse por contrato. */
    public static final int EDAD_MINIMA = 18;

    /* ── Entrada ── */

    /**
     * Domicilio del solicitante, con la división que usa Perú.
     *
     * <p>Va agrupado y no suelto entre los demás campos porque es una unidad:
     * se pinta como un bloque en el formulario y se lee como un bloque en la
     * bandeja.
     */
    public record Domicilio(
            @NotBlank @Size(max = 200) String direccion,
            @Size(max = 200) String referencia,
            @NotBlank @Size(max = 80) String distrito,
            @NotBlank @Size(max = 80) String provincia,
            @NotBlank @Size(max = 80) String departamento,
            @NotBlank @Pattern(regexp = "^\\d{5}$",
                    message = "El código postal son 5 dígitos") String codigoPostal,
            @Pattern(regexp = "^[A-Z]{2}$",
                    message = "El país es un código de dos letras, como PE") String pais,
            /*
             * El punto en el mapa, opcional. Los rangos se validan porque unas
             * coordenadas imposibles no salen de un GPS: salen de un enlace mal
             * pegado, y guardarlas dejaría un mapa apuntando al vacío.
             */
            @DecimalMin(value = "-90.0", message = "La latitud está fuera de rango")
            @DecimalMax(value = "90.0", message = "La latitud está fuera de rango") BigDecimal latitud,
            @DecimalMin(value = "-180.0", message = "La longitud está fuera de rango")
            @DecimalMax(value = "180.0", message = "La longitud está fuera de rango") BigDecimal longitud) {

        public Domicilio {
            direccion = Saneador.texto(direccion);
            referencia = Saneador.texto(referencia);
            distrito = Saneador.texto(distrito);
            provincia = Saneador.texto(provincia);
            departamento = Saneador.texto(departamento);
            codigoPostal = Saneador.texto(codigoPostal);
            // Casi todo el mundo vende desde Perú: se asume en vez de obligar a
            // rellenarlo. Sigue siendo un campo por si algún día deja de serlo.
            pais = (pais == null || pais.isBlank()) ? "PE" : Saneador.texto(pais).toUpperCase();
            // O van las dos o no va ninguna: media coordenada no ubica nada.
            if (latitud == null || longitud == null) {
                latitud = null;
                longitud = null;
            }
        }
    }

    /**
     * Lo que envía el solicitante.
     *
     * <p>El {@code usuarioId} NO viaja aquí a propósito: sale del token. Si se
     * aceptara por el cuerpo, cualquiera podría enviar una solicitud a nombre
     * de otro.
     *
     * <p>Los adjuntos tampoco viajan aquí: se suben antes, uno a uno, y al
     * llegar a este punto ya están esperando asociados al usuario. Así una
     * validación fallida no obliga a volver a subir varios megas de fotos.
     *
     * <p>Los topes coinciden con los de las columnas: si no, una cadena larga no
     * la rechaza la validación sino Postgres, y el usuario recibe un 500 en vez
     * de un 400 que le diga qué campo arreglar.
     */
    public record SolicitudRequest(
            // ── Quién es ──
            @NotNull(message = "Indica si solicitas como persona o como empresa")
            TipoPersona tipoPersona,
            @NotNull(message = "Indica el tipo de documento") TipoDocumento tipoDocumento,
            @NotBlank @Size(max = 12) String documento,
            @NotBlank @Size(min = 3, max = 160,
                    message = "Escribe el nombre completo tal como aparece en el documento")
            String nombreTitular,
            @Size(max = 160) String representanteLegal,
            @Past(message = "La fecha de nacimiento tiene que estar en el pasado")
            LocalDate fechaNacimiento,

            // ── El negocio ──
            @NotBlank @Size(min = 3, max = 120) String nombreComercial,
            @NotBlank @Pattern(regexp = "\\d{9}",
                    message = "El teléfono debe tener 9 dígitos") String telefonoContacto,
            @NotBlank @Size(max = 120) String rubro,
            @NotBlank @Size(min = 30, max = 1000,
                    message = "Cuéntanos algo más: entre 30 y 1000 caracteres") String descripcion,

            // ── Dónde ──
            @NotNull(message = "Falta el domicilio") @Valid Domicilio domicilio,

            // ── Condiciones ──
            @AssertTrue(message = "Hay que aceptar los términos para vender en la tienda")
            Boolean aceptaTerminos,
            @NotBlank(message = "Falta la versión de los términos que se mostró")
            String terminosVersion) {

        /**
         * A03: se limpia en el constructor compacto, antes de que corra la
         * validación. Así {@code @NotBlank} ve el texto ya recortado y lo que se
         * guarda es la forma normalizada, igual que en el resto del proyecto.
         */
        public SolicitudRequest {
            documento = Saneador.texto(documento);
            documento = documento == null ? null : documento.toUpperCase();
            nombreTitular = Saneador.texto(nombreTitular);
            representanteLegal = Saneador.texto(representanteLegal);
            nombreComercial = Saneador.texto(nombreComercial);
            telefonoContacto = Saneador.texto(telefonoContacto);
            rubro = Saneador.texto(rubro);
            descripcion = Saneador.textoMultilinea(descripcion);
            terminosVersion = Saneador.texto(terminosVersion);
        }

        /**
         * Las reglas que cruzan campos.
         *
         * <p>No se pueden poner como anotación porque cada una mira dos o tres
         * campos a la vez. Están juntas y no repartidas por el servicio para que
         * se lean como lo que son: la definición de qué solicitud es válida.
         *
         * <p>La base comprueba lo mismo con CHECK. Aquí se hace para dar un
         * mensaje que se pueda enseñar; allí, para que nada entre por otra vía.
         */
        public void validarCoherencia() {
            if (!tipoPersona.admite(tipoDocumento)) {
                throw new DatosInvalidosException(tipoPersona == TipoPersona.NATURAL
                        ? "Como persona, identifícate con DNI o carné de extranjería, no con RUC"
                        : "Una empresa se identifica con su RUC");
            }

            if (!tipoDocumento.valida(documento)) {
                throw new DatosInvalidosException(tipoDocumento.mensaje());
            }

            if (tipoPersona.exigeRepresentante() && esVacio(representanteLegal)) {
                throw new DatosInvalidosException(
                        "Indica el representante legal de la empresa");
            }
            if (!tipoPersona.exigeRepresentante() && !esVacio(representanteLegal)) {
                throw new DatosInvalidosException(
                        "El representante legal solo corresponde a una empresa");
            }

            if (tipoPersona.exigeFechaNacimiento()) {
                if (fechaNacimiento == null) {
                    throw new DatosInvalidosException("Indica tu fecha de nacimiento");
                }
                if (Period.between(fechaNacimiento, LocalDate.now()).getYears() < EDAD_MINIMA) {
                    throw new DatosInvalidosException(
                            "Hay que ser mayor de %d años para vender en la tienda"
                                    .formatted(EDAD_MINIMA));
                }
            } else if (fechaNacimiento != null) {
                throw new DatosInvalidosException(
                        "La fecha de nacimiento solo corresponde a una persona");
            }
        }

        private static boolean esVacio(String valor) {
            return valor == null || valor.isBlank();
        }
    }

    /** Motivo del rechazo. Se le enseña al solicitante, así que tiene que ser útil. */
    public record RechazoRequest(
            @NotBlank @Size(min = 10, max = 500,
                    message = "Explica el motivo en entre 10 y 500 caracteres") String motivo) {

        public RechazoRequest {
            motivo = Saneador.textoMultilinea(motivo);
        }
    }

    /* ── Salida ── */

    /**
     * Ficha de un adjunto. Nunca los bytes: para verlos hay que pedirlos al
     * endpoint de descarga, que comprueba quién pregunta.
     */
    public record AdjuntoResponse(
            Long id,
            TipoAdjunto tipo,
            String etiqueta,
            String nombreOriginal,
            String tipoMime,
            Long tamanoBytes,
            Instant subidoEn,
            boolean disponible) {

        public static AdjuntoResponse desde(DocumentoIdentidad d) {
            return new AdjuntoResponse(
                    d.getId(), d.getTipo(), d.getTipo().etiqueta(), d.getNombreOriginal(),
                    d.getTipoMime(), d.getTamanoBytes(), d.getSubidoEn(), !d.estaPurgado());
        }
    }

    /** Domicilio ya guardado. */
    public record DomicilioResponse(
            String direccion, String referencia, String distrito,
            String provincia, String departamento, String codigoPostal, String pais,
            BigDecimal latitud, BigDecimal longitud) {

        public static DomicilioResponse desde(SolicitudColaborador s) {
            return new DomicilioResponse(
                    s.getDireccion(), s.getReferencia(), s.getDistrito(),
                    s.getProvincia(), s.getDepartamento(), s.getCodigoPostal(), s.getPais(),
                    s.getLatitud(), s.getLongitud());
        }
    }

    /** Lo que ve el propio solicitante. */
    public record SolicitudResponse(
            Long id,
            EstadoSolicitud estado,
            TipoPersona tipoPersona,
            TipoDocumento tipoDocumento,
            String documento,
            String nombreTitular,
            String representanteLegal,
            LocalDate fechaNacimiento,
            String nombreComercial,
            String telefonoContacto,
            String rubro,
            String descripcion,
            DomicilioResponse domicilio,
            List<AdjuntoResponse> adjuntos,
            String motivoRechazo,
            Instant creadaEn,
            Instant resueltaEn) {

        public static SolicitudResponse desde(SolicitudColaborador s, List<DocumentoIdentidad> adjuntos) {
            return new SolicitudResponse(
                    s.getId(), s.getEstado(), s.getTipoPersona(), s.getTipoDocumento(),
                    s.getDocumento(), s.getNombreTitular(), s.getRepresentanteLegal(),
                    s.getFechaNacimiento(), s.getNombreComercial(), s.getTelefonoContacto(),
                    s.getRubro(), s.getDescripcion(), DomicilioResponse.desde(s),
                    adjuntos.stream().map(AdjuntoResponse::desde).toList(),
                    s.getMotivoRechazo(), s.getCreadaEn(), s.getResueltaEn());
        }
    }

    /** Datos mínimos del solicitante para que el administrador decida. */
    public record Solicitante(Long id, String nombreCompleto, String email, String rol) {

        public static Solicitante desde(Usuario u) {
            return new Solicitante(
                    u.getId(),
                    "%s %s".formatted(u.getName(), u.getLastname()).trim(),
                    u.getEmailAddress(),
                    u.getRol());
        }
    }

    /**
     * Lo que ve el administrador: la solicitud más quién la envía.
     *
     * <p>Es un tipo aparte y no un campo opcional del anterior porque el
     * solicitante no debe viajar nunca hacia el propio usuario: ya sabe quién
     * es, y devolverlo solo amplía lo que se expone sin motivo.
     */
    public record SolicitudAdminResponse(
            Long id,
            EstadoSolicitud estado,
            TipoPersona tipoPersona,
            TipoDocumento tipoDocumento,
            String documento,
            String nombreTitular,
            String representanteLegal,
            LocalDate fechaNacimiento,
            String nombreComercial,
            String telefonoContacto,
            String rubro,
            String descripcion,
            DomicilioResponse domicilio,
            List<AdjuntoResponse> adjuntos,
            String motivoRechazo,
            Instant creadaEn,
            Instant resueltaEn,
            Solicitante solicitante) {

        public static SolicitudAdminResponse desde(SolicitudColaborador s, Usuario u,
                List<DocumentoIdentidad> adjuntos) {
            return new SolicitudAdminResponse(
                    s.getId(), s.getEstado(), s.getTipoPersona(), s.getTipoDocumento(),
                    s.getDocumento(), s.getNombreTitular(), s.getRepresentanteLegal(),
                    s.getFechaNacimiento(), s.getNombreComercial(), s.getTelefonoContacto(),
                    s.getRubro(), s.getDescripcion(), DomicilioResponse.desde(s),
                    adjuntos.stream().map(AdjuntoResponse::desde).toList(),
                    s.getMotivoRechazo(), s.getCreadaEn(), s.getResueltaEn(),
                    Solicitante.desde(u));
        }
    }

    /** Respuesta a una subida: lo justo para que el formulario pinte el archivo. */
    public record SubidaResponse(Long id, TipoAdjunto tipo, String etiqueta,
            String nombreOriginal, String tipoMime, Long tamanoBytes) {

        public static SubidaResponse desde(DocumentoIdentidad d) {
            return new SubidaResponse(d.getId(), d.getTipo(), d.getTipo().etiqueta(),
                    d.getNombreOriginal(), d.getTipoMime(), d.getTamanoBytes());
        }
    }
}
