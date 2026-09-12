package com.backend.catalogo.descubrimiento.dto;

import java.util.List;
import java.util.UUID;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.TipoEvento;
import com.backend.catalogo.descubrimiento.TipoItem;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.validacion.Saneador;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class DescubrimientoDtos {

    private DescubrimientoDtos() {
    }

    /**
     * Un evento suelto.
     *
     * <p>El {@code sujetoId} NO viaja aquí: lo resuelve el servidor a partir del
     * JWT o de la cabecera. Aceptarlo en el cuerpo permitiría escribir en el
     * perfil de cualquiera.
     */
    public record EventoRequest(
            @NotNull TipoEvento tipo,
            TipoItem itemTipo,
            @Positive Long itemId,
            @Positive Long categoriaId,
            @PositiveOrZero Integer dwellMs,
            @Size(max = 32) String origen,
            @PositiveOrZero Short posicion,
            /** Solo para ATTRIBUTE_FILTER: el código del atributo filtrado. */
            @Size(max = 60) String atributoCodigo,
            @Size(max = 200) String atributoValor,
            @Size(max = 200) String termino) {

        public EventoRequest {
            origen = Saneador.textoONulo(origen);
            atributoCodigo = Saneador.textoONulo(atributoCodigo);
            atributoValor = Saneador.textoONulo(atributoValor);
            termino = Saneador.textoONulo(termino);
        }
    }

    /**
     * Los eventos llegan en lote, no de uno en uno.
     *
     * <p>Explorar genera decenas de eventos por minuto. Una petición por evento
     * sería más tráfico que el propio catálogo y castigaría justo a quien navega
     * con datos móviles contados, que es la mayoría en Perú.
     */
    public record LoteEventosRequest(
            /*
             * El sujeto y su firma, para `navigator.sendBeacon`.
             *
             * Van en el CUERPO y no en la query string, que es de donde se
             * retiraron: una URL acaba escrita en logs de acceso, en proxies,
             * en el historial y en la cabecera `Referer`, y con la firma al lado
             * eso convertia cualquier linea de log en una credencial que
             * funciona. sendBeacon no puede poner cabeceras, pero si mandar un
             * cuerpo JSON, asi que aqui es donde tienen que ir.
             *
             * Nulos en el camino normal: ahi viajan como cabeceras.
             */
            UUID sujeto,
            @Size(max = 64) String firma,
            @NotNull UUID sesionId,
            /** Ubigeo INEI del distrito. Nunca coordenadas. */
            @Pattern(regexp = "^[0-9]{6}$", message = "El ubigeo son seis dígitos") String ubigeo,
            @NotEmpty @Size(max = 100) List<@Valid EventoRequest> eventos) {
    }

    /** Lo que se mostró en un carrusel, se tocara o no. */
    public record ImpresionRequest(
            @NotNull TipoItem itemTipo,
            @NotNull @Positive Long itemId,
            @NotNull @Size(max = 40) String modulo,
            @PositiveOrZero Short posicion) {
    }

    public record LoteImpresionesRequest(
            /** Igual que en los eventos: en el cuerpo, nunca en la URL. */
            UUID sujeto,
            @Size(max = 64) String firma,
            @NotEmpty @Size(max = 200) List<@Valid ImpresionRequest> impresiones) {
    }

    /**
     * Un carrusel ya resuelto.
     *
     * <p>{@code origen} y {@code motivo} viajan a propósito: la interfaz tiene
     * que poder distinguir «según tus intereses» de «lo más visto en Ica», y
     * decir por qué. Presentar una tendencia general como personal es lo que
     * hace que un recomendador se sienta falso.
     */
    public record Carrusel(
            String modulo,
            String titulo,
            Origen origen,
            String motivo,
            List<ProductoResponse> items) {
    }

    /**
     * La respuesta del Home.
     *
     * <p>Incluye el {@code sujetoId} para que un visitante anónimo pueda
     * guardarlo y volver con él. Es lo que permite que su rastro sobreviva a
     * cerrar la pestaña y, más adelante, se funda con su cuenta.
     *
     * <p>Y la {@code firma} que lo acompaña, porque desde ahora el
     * identificador solo vale con ella: conocerlo dejó de ser suficiente para
     * usarlo. Las dos cosas se devuelven juntas y solo a su dueño.
     */
    public record HomeResponse(UUID sujetoId, String firma, List<Carrusel> carruseles) {
    }

    /** Una faceta del perfil, para la pantalla «Tus intereses». */
    public record InteresResponse(String tipo, String faceta, double score, int eventos) {
    }

    /**
     * Confirmación de la ingesta, con el sujeto que el cliente debe recordar.
     *
     * <p>{@code descartados} no es un detalle de diagnóstico: son impresiones
     * que el servidor no pudo casar con nada que le hubiera servido a ese
     * sujeto. Un cliente legítimo verá siempre cero.
     */
    public record IngestaResponse(UUID sujetoId, String firma, int registrados,
            int descartados) {
    }
}
