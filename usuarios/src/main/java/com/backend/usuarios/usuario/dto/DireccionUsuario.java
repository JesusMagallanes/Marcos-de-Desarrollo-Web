package com.backend.usuarios.usuario.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Usuario;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * La dirección de entrega guardada en el perfil.
 *
 * <p>Antes se preguntaba en el carrito, cada vez, y no se guardaba: la misma
 * persona reescribía su casa en cada compra, y el único rastro que quedaba era
 * una línea de texto libre. Ahora se pone una vez aquí y el checkout solo
 * pregunta si el pedido va ahí o a otro sitio.
 *
 * <h4>Qué NO lleva</h4>
 *
 * <p>Ni el nombre de quien recibe ni el teléfono: el perfil ya los tiene. Se
 * usan como valor de partida al pagar, y ahí sí se pueden cambiar —un regalo va
 * a nombre de otro— pero duplicar dos columnas que ya existen solo garantiza que
 * algún día digan cosas distintas.
 *
 * <h4>La jerarquía es la de Perú</h4>
 *
 * <p>Departamento &gt; provincia &gt; distrito.
 */
public record DireccionUsuario(

        @NotBlank(message = "Indica la calle o avenida")
        @Size(max = 200) String calle,

        @NotBlank(message = "Indica el número de la puerta")
        @Size(max = 20) String numero,

        /** Piso, interior, «la casa del portón verde». Es lo que salva la entrega. */
        @Size(max = 200) String referencia,

        @NotBlank(message = "Indica el código postal")
        @Pattern(regexp = "^[0-9]{5}$",
                message = "El código postal son 5 dígitos") String codigoPostal,

        @NotBlank(message = "Indica el distrito") @Size(max = 80) String distrito,
        @NotBlank(message = "Indica la provincia") @Size(max = 80) String provincia,
        @NotBlank(message = "Indica el departamento") @Size(max = 80) String departamento,

        @Pattern(regexp = "^[A-Z]{2}$",
                message = "El país es un código de dos letras, como PE") String pais,

        /*
         * El punto en el mapa. OPCIONAL: nadie se queda sin comprar por no
         * compartir su posición, y sin él la entrega funciona igual; lo que se
         * pierde es el cálculo de distancia para quien reparte.
         */
        @DecimalMin(value = "-90.0", message = "La latitud está fuera de rango")
        @DecimalMax(value = "90.0", message = "La latitud está fuera de rango") BigDecimal latitud,
        @DecimalMin(value = "-180.0", message = "La longitud está fuera de rango")
        @DecimalMax(value = "180.0", message = "La longitud está fuera de rango") BigDecimal longitud) {

    public DireccionUsuario {
        calle = Saneador.texto(calle);
        numero = Saneador.texto(numero);
        referencia = Saneador.texto(referencia);
        codigoPostal = Saneador.texto(codigoPostal);
        distrito = Saneador.texto(distrito);
        provincia = Saneador.texto(provincia);
        departamento = Saneador.texto(departamento);

        // Casi todo el mundo compra desde Perú: se asume en vez de obligar a
        // rellenarlo. Sigue siendo un campo por si algún día deja de serlo.
        pais = (pais == null || pais.isBlank()) ? "PE" : Saneador.texto(pais).toUpperCase();

        // O van las dos o no va ninguna: media coordenada no ubica nada.
        if (latitud == null || longitud == null) {
            latitud = null;
            longitud = null;
        }
    }

    /** Lo que hay guardado, o vacío si el usuario todavía no puso ninguna. */
    public static DireccionUsuario desde(Usuario u) {
        if (!u.tieneDireccionCompleta()) {
            return null;
        }
        return new DireccionUsuario(
                u.getDirCalle(), u.getDirNumero(), u.getDirReferencia(), u.getDirCodigoPostal(),
                u.getDirDistrito(), u.getDirProvincia(), u.getDirDepartamento(), u.getDirPais(),
                u.getDirLatitud(), u.getDirLongitud());
    }

    /** Vuelca la dirección sobre el usuario, línea compuesta incluida. */
    public void aplicarA(Usuario u) {
        u.setDirCalle(calle);
        u.setDirNumero(numero);
        u.setDirReferencia(referencia);
        u.setDirCodigoPostal(codigoPostal);
        u.setDirDistrito(distrito);
        u.setDirProvincia(provincia);
        u.setDirDepartamento(departamento);
        u.setDirPais(pais);
        u.setDirLatitud(latitud);
        u.setDirLongitud(longitud);
        u.setAddress(enUnaLinea());
    }

    /**
     * La línea que se lee de un vistazo y la que se imprime.
     *
     * <p>Se compone desde las partes en vez de pedirla escrita: así lo que se
     * imprime y lo que se le manda a la pasarela no pueden contradecirse.
     */
    public String enUnaLinea() {
        List<String> trozos = new ArrayList<>();
        trozos.add(calle + " " + numero);
        trozos.add(distrito);
        trozos.add(provincia);
        if (!provincia.equalsIgnoreCase(departamento)) {
            // Lima, Lima, Lima no le dice nada a nadie.
            trozos.add(departamento);
        }
        return String.join(", ", trozos);
    }
}
