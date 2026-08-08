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
        USUARIO_ELIMINADO,
        ACCESO_DENEGADO
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
