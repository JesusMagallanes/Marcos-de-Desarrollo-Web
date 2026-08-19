package com.backend.compras.pago.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.backend.compras.shared.validacion.Saneador;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A dónde va el pedido, en partes.
 *
 * <p>Antes era una sola línea de texto donde el comprador escribía lo que
 * quería. Servía para imprimir una etiqueta y para nada más: no se puede
 * calcular un costo de envío sin código postal, ni agrupar reparto por distrito,
 * ni mandarle la dirección a la pasarela, que la quiere en campos separados.
 *
 * <p>Se pide al INICIAR el checkout y no al confirmarlo: el comprador todavía
 * está en la tienda, y después se va a MercadoPago y puede no volver. Así el
 * importe y el destino quedan fijados juntos antes de pagar.
 *
 * <h4>La jerarquía es la de Perú</h4>
 *
 * <p>Departamento &gt; provincia &gt; distrito. MercadoPago usa la suya
 * —state &gt; city &gt; neighborhood— y la equivalencia se hace en
 * {@link #comoReceiverAddress()}, en un solo sitio, para que nadie tenga que
 * acordarse de que «provincia» de aquí es «city» de allí.
 */
public record DireccionEntrega(

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
         * Quien recibe no siempre es quien compra: un regalo, un envío a la
         * oficina. Sin este dato el repartidor pregunta por el titular de la
         * cuenta y en esa dirección no lo conoce nadie.
         */
        @NotBlank(message = "Indica quién recibe el pedido")
        @Size(min = 3, max = 120) String receptorNombre,

        @NotBlank(message = "Necesitamos un teléfono para coordinar la entrega")
        @Pattern(regexp = "^[0-9]{9}$",
                message = "El teléfono debe tener 9 dígitos") String telefonoContacto,

        /*
         * El punto en el mapa. OPCIONAL: nadie se queda sin comprar por no
         * compartir su posición, y sin él la entrega funciona igual; lo que se
         * pierde es el cálculo de distancia para quien reparte.
         */
        @DecimalMin(value = "-90.0", message = "La latitud está fuera de rango")
        @DecimalMax(value = "90.0", message = "La latitud está fuera de rango") BigDecimal latitud,
        @DecimalMin(value = "-180.0", message = "La longitud está fuera de rango")
        @DecimalMax(value = "180.0", message = "La longitud está fuera de rango") BigDecimal longitud) {

    public DireccionEntrega {
        calle = Saneador.texto(calle);
        numero = Saneador.texto(numero);
        referencia = Saneador.texto(referencia);
        codigoPostal = Saneador.texto(codigoPostal);
        distrito = Saneador.texto(distrito);
        provincia = Saneador.texto(provincia);
        departamento = Saneador.texto(departamento);
        receptorNombre = Saneador.texto(receptorNombre);
        telefonoContacto = Saneador.texto(telefonoContacto);

        // Casi todo el mundo compra desde Perú: se asume en vez de obligar a
        // rellenarlo. Sigue siendo un campo por si algún día deja de serlo.
        pais = (pais == null || pais.isBlank()) ? "PE" : Saneador.texto(pais).toUpperCase();

        // O van las dos o no va ninguna: media coordenada no ubica nada y
        // pintaría un mapa en mitad del océano, que es peor que no tener punto
        // porque parece un dato.
        if (latitud == null || longitud == null) {
            latitud = null;
            longitud = null;
        }
    }

    /**
     * La línea que se imprime en la etiqueta y que se ve en el panel de envíos.
     *
     * <p>Se compone aquí, a partir de las partes, en vez de pedirle al comprador
     * que la escriba a mano: así lo que se imprime y lo que se le manda a la
     * pasarela no pueden contradecirse.
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

    /**
     * La dirección tal como la quiere MercadoPago dentro de {@code shipments}.
     *
     * <p>Con esto la pasarela enseña el destino en su propia pantalla y puede
     * calcular el envío; sin ello el comprador ve solo un importe y un nombre de
     * producto, y tiene que fiarse.
     *
     * <p>Los nombres son los de la pasarela, no los nuestros, y esa traducción
     * vive solo aquí. La equivalencia para Perú es {@code state} =
     * departamento, {@code city} = provincia y {@code neighborhood} = distrito.
     */
    public Map<String, Object> comoReceiverAddress() {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("zip_code", codigoPostal);
        direccion.put("street_name", calle);
        direccion.put("street_number", numero);
        // El distrito va en `neighborhood`, y no es un detalle: en Perú es lo que
        // decide el reparto. «Av. Los Próceres 1420» hay en varios distritos.
        direccion.put("neighborhood", distrito);
        direccion.put("city_name", provincia);
        direccion.put("state_name", departamento);
        direccion.put("country_name", pais);

        // `floor` y `apartment` son los campos que la pasarela reserva para el
        // piso y el interior. No los pedimos por separado —al comprador se le
        // pregunta una sola cosa, «referencia»— así que va entera en `floor`,
        // que es donde MercadoPago la enseña.
        if (referencia != null && !referencia.isBlank()) {
            direccion.put("floor", referencia);
        }
        return direccion;
    }
}
