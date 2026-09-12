package com.backend.catalogo.descubrimiento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Convierte el identificador de sujeto en algo que hay que poseer, no adivinar.
 *
 * <h4>El problema que cierra</h4>
 *
 * <p>El identificador de sujeto anónimo funcionaba como una credencial sin
 * estarlo. Quien lo consiguiera —de un log, de una URL, de un equipo
 * compartido— lo mandaba en {@code X-Sujeto} y el backend le entregaba esa
 * identidad entera: su perfil de intereses, el botón de borrarlo y la
 * posibilidad de envenenarlo con eventos. No hacía falta romper nada; bastaba
 * con copiar una cadena.
 *
 * <p>Ahora el identificador viaja acompañado de una firma que solo el servidor
 * sabe calcular. Sin ella, el identificador no vale: la petición recibe un
 * sujeto nuevo, como quien llega por primera vez.
 *
 * <h4>Por qué HMAC y no un token con estado</h4>
 *
 * <p>Porque no hace falta más. Un HMAC es autocontenido: no necesita tabla,
 * ni caché, ni Redis, ni un proceso que lo caduque. El servidor recalcula y
 * compara. Guardar tokens habría añadido infraestructura para responder una
 * pregunta que la aritmética responde sola.
 *
 * <h4>Lo que la firma protege y lo que no</h4>
 *
 * <p>Protege de las tres cosas que importaban: cambiar el identificador,
 * inventarse uno, y usar el de otra persona. No es autenticación y no pretende
 * serlo — un sujeto anónimo sigue sin ser nadie. Cuando hay cuenta, el JWT
 * manda y esto no se mira.
 */
@Component
public class FirmaSujeto {

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] secreto;

    /**
     * El secreto sale del que ya existe, y por eso no hay variable nueva.
     *
     * <p>Se deriva de {@code JWT_SECRET}, que es obligatorio para arrancar y ya
     * está en todos los entornos. Se puede sustituir con
     * {@code descubrimiento.sujeto.secreto} si algún día conviene rotarlos por
     * separado, pero pedir una variable nueva habría obligado a tocar el
     * despliegue para cerrar una vulnerabilidad, que es la peor manera de
     * conseguir que una corrección no se aplique.
     *
     * <p>La derivación mete un propósito fijo en el hash: así la firma de un
     * sujeto no es utilizable como ninguna otra cosa que se firme con la misma
     * clave, ni al revés.
     */
    public FirmaSujeto(
            @Value("${descubrimiento.sujeto.secreto:${JWT_SECRET:}}") String configurado) {

        if (configurado == null || configurado.isBlank()) {
            throw new IllegalStateException(
                    "Falta el secreto para firmar sujetos: define JWT_SECRET"
                            + " o descubrimiento.sujeto.secreto");
        }
        this.secreto = hmac(configurado.getBytes(StandardCharsets.UTF_8),
                "smartzone:descubrimiento:sujeto:v1");
    }

    /** La credencial que acompaña a un identificador de sujeto. */
    public String de(UUID sujeto) {
        byte[] firma = hmac(secreto, sujeto.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
    }

    /**
     * Si esta firma corresponde a este identificador.
     *
     * <p>La comparación es en tiempo constante. Comparar con {@code equals}
     * termina en el primer byte distinto, y el tiempo que tarda en fallar dice
     * cuántos bytes se acertaron: con suficientes intentos eso permite
     * construir una firma válida byte a byte. Aquí no es explotable a través de
     * la red con facilidad, pero escribir la versión insegura de una
     * comparación criptográfica es una costumbre que acaba pagándose en otro
     * sitio.
     */
    public boolean valida(UUID sujeto, String firma) {
        if (sujeto == null || firma == null || firma.isBlank()) {
            return false;
        }
        byte[] esperada = de(sujeto).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = firma.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, recibida);
    }

    private static byte[] hmac(byte[] clave, String mensaje) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(clave, ALGORITMO));
            return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException imposible) {
            // HmacSHA256 es obligatorio en toda JVM; si falta, el problema es otro.
            throw new IllegalStateException("No se pudo firmar el sujeto", imposible);
        }
    }
}
