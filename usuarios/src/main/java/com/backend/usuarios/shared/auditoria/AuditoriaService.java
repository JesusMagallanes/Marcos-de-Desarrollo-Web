package com.backend.usuarios.shared.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** A09 (Fallos de registro y monitorización). */
@Service
public class AuditoriaService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("AUDITORIA");

    public enum Evento {
        LOGIN_OK,
        LOGIN_FALLIDO,
        REGISTRO,
        LOGIN_OAUTH,
        CAMBIO_ROL,
        ROL_CREADO,
        ROL_EDITADO,
        ROL_ELIMINADO,
        USUARIO_ELIMINADO,
        ACCESO_DENEGADO,

        /* Solicitudes para vender en la tienda. Quedan en auditoría porque
         * aprobar a alguien le da permiso para publicar en el catálogo, y hay
         * que poder reconstruir quién concedió qué y cuándo. */
        SOLICITUD_COLABORADOR,
        COLABORADOR_APROBADO,
        COLABORADOR_RECHAZADO,

        /* Documentos de identidad. La descarga se audita aparte y siempre,
         * incluso cuando la hace el propio dueño: son los datos más sensibles
         * que guarda el sistema, y "quién miró el DNI de quién" es exactamente
         * lo que hay que poder responder después. */
        DOCUMENTO_SUBIDO,
        DOCUMENTO_DESCARGADO,
        DOCUMENTO_PURGADO
    }

    public void registrar(Evento evento, String sujeto, String detalle) {
        AUDITORIA.info("evento={} sujeto={} detalle={}", evento, ofuscar(sujeto), detalle);
    }

    public void registrarFallo(Evento evento, String sujeto, String motivo) {
        AUDITORIA.warn("evento={} sujeto={} motivo={}", evento, ofuscar(sujeto), motivo);
    }

    /**
     * Los correos son datos personales: en los registros se guarda una versión parcial,
     * suficiente para correlacionar sin volcar la lista de clientes en los ficheros de
     * log.
     */
    private String ofuscar(String correo) {
        if (correo == null || correo.isBlank()) {
            return "desconocido";
        }
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return "***" + (arroba >= 0 ? correo.substring(arroba) : "");
        }
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
