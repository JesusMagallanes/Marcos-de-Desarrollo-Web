package com.backend.catalogo.descubrimiento;

import java.util.LinkedHashSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.HomeResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.IngestaResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.InteresResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteEventosRequest;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteImpresionesRequest;
import com.backend.catalogo.shared.seguridad.JwtUtils;

import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.shared.seguridad.IpCliente;
import com.backend.catalogo.shared.seguridad.LimitadorPeticiones;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * La cara pública del descubrimiento.
 *
 * <h4>Cómo se sabe quién pregunta</h4>
 *
 * <p>Dos vías, y una manda sobre la otra: si hay JWT, el sujeto sale de la
 * cuenta y la cabecera {@code X-Sujeto} SE IGNORA. Solo se hace caso a la
 * cabecera cuando no hay sesión iniciada.
 *
 * <p>No es un detalle de implementación: sin esa precedencia, cualquiera podría
 * leer o contaminar el perfil de otro poniendo su identificador en una
 * cabecera. Está cubierto por prueba en {@code SujetoServiceTest}.
 *
 * <h4>Por qué casi todo es público</h4>
 *
 * <p>El grueso del tráfico de descubrimiento viene de gente sin cuenta —es
 * literalmente el caso de uso: entrar «solo a mirar»—. Exigir sesión para
 * registrar eventos dejaría al sistema sin la señal que vino a capturar.
 */
@RestController
@RequestMapping("/api/descubrimiento")
@Validated
@RequiredArgsConstructor
public class DescubrimientoController {

    /**
     * La firma que acompaña al identificador de sujeto.
     *
     * <p>En una cabecera propia y no dentro de {@code X-Sujeto}: así el
     * identificador sigue siendo un UUID a secas para quien lo lea, y añadir la
     * credencial no obligó a cambiar el formato de nada de lo que ya existía.
     */
    public static final String CABECERA_FIRMA = "X-Sujeto-Firma";

    private static final int MAXIMO_POR_CARRUSEL = 24;

    private final SujetoService sujetos;
    private final IngestaService ingesta;
    private final RecomendacionService recomendador;
    private final PerfilService perfiles;
    private final FirmaSujeto firmas;
    private final LimitadorPeticiones limitador;
    private final MetricasSeguridad seguridad;
    private final IpCliente ipCliente;
    private final PesosDescubrimiento pesos;

    /* ══════════════ Escritura ══════════════ */

    /**
     * Registra un lote de eventos.
     *
     * <p>Devuelve el {@code sujetoId} para que un visitante anónimo lo guarde y
     * vuelva con él: es lo que hace que su rastro sobreviva a cerrar la pestaña
     * y, el día que inicie sesión, se funda con su cuenta.
     */
    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestaResponse eventos(
            @Valid @RequestBody LoteEventosRequest lote,
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @AuthenticationPrincipal Jwt jwt) {

        /*
         * El sujeto ya no viaja en la query string.
         *
         * Estaba ahi para `navigator.sendBeacon`, que no admite cabeceras, y
         * era un agujero: una URL termina en logs de acceso, proxies, historial
         * y `Referer`. Ahora sendBeacon manda el identificador y su firma
         * dentro del CUERPO, que no se escribe en ninguno de esos sitios.
         */
        UUID sujeto = resolver(jwt,
                sujetoDelCliente != null ? sujetoDelCliente : lote.sujeto(),
                firmaDelCliente != null ? firmaDelCliente : lote.firma(),
                lote.ubigeo());
        comprobarCupoDe(sujeto);

        return respuesta(sujeto, ingesta.registrar(sujeto, lote), 0);
    }

    /**
     * Registra qué se mostró.
     *
     * <p>Va aparte de los eventos porque el volumen es otro: un Home pinta unos
     * sesenta ítems y se hace clic en uno. Sin esto no se puede calcular el CTR
     * de un carrusel ni dejar de insistir con lo que ya se ignoró.
     */
    @PostMapping("/impresiones")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IngestaResponse impresiones(
            @Valid @RequestBody LoteImpresionesRequest lote,
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt,
                sujetoDelCliente != null ? sujetoDelCliente : lote.sujeto(),
                firmaDelCliente != null ? firmaDelCliente : lote.firma(),
                null);
        comprobarCupoDe(sujeto);

        IngestaService.Resultado r = ingesta.registrarImpresiones(sujeto, lote.impresiones());
        return respuesta(sujeto, r.aceptadas(), r.descartadas());
    }

    /* ══════════════ Lectura ══════════════ */

    /** El Home entero, ya de-duplicado entre carruseles. */
    @GetMapping("/home")
    public HomeResponse home(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "El ubigeo son seis dígitos") String ubigeo,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int porCarrusel,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, firmaDelCliente, ubigeo);
        return new HomeResponse(sujeto, firmas.de(sujeto),
                recomendador.home(sujeto, ubigeo, porCarrusel));
    }

    @GetMapping("/segun-intereses")
    public Carrusel segunIntereses(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, firmaDelCliente, null);
        return vacioSiFalta(ModuloDescubrimiento.SEGUN_TUS_INTERESES,
                recomendador.segunIntereses(sujeto, limite, new LinkedHashSet<>()));
    }

    /**
     * Relacionados con un producto.
     *
     * <p>Funciona SIN sujeto: el parecido por contenido se resuelve con la ficha
     * del producto, así que un visitante que llega desde un buscador y no ha
     * hecho nada todavía ve un carrusel útil igualmente.
     *
     * <p>Pero si el sujeto viene, se usa, y eso cambia dos cosas importantes.
     * La primera es que los filtros duros se aplican: hasta ahora esta pantalla
     * los ignoraba, de modo que un producto marcado como «no me interesa»
     * reaparecía en la ficha de cualquier otro. La segunda es que lo servido
     * queda anotado, y sin eso esta superficie —que es de las más visitadas—
     * quedaba fuera de toda la medición.
     *
     * <p>El sujeto NO se crea aquí. Si el visitante no trae ninguno y no hay
     * JWT, se sigue adelante con {@code null} en vez de acuñar una identidad:
     * un rastreador recorriendo el catálogo llenaría la tabla de sujetos que no
     * son nadie, y una recomendación no es motivo para empezar a seguir a
     * alguien que no ha interactuado.
     */
    @GetMapping("/similares/{itemId}")
    public Carrusel similares(
            @PathVariable @Positive Long itemId,
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite) {

        UUID sujeto = (jwt != null || sujetoDelCliente != null)
                ? resolver(jwt, sujetoDelCliente, firmaDelCliente, null)
                : null;

        return vacioSiFalta(ModuloDescubrimiento.RELACIONADOS,
                recomendador.similares(itemId, sujeto, limite));
    }

    @GetMapping("/tendencias")
    public Carrusel tendencias(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "El ubigeo son seis dígitos") String ubigeo,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, firmaDelCliente, ubigeo);
        return vacioSiFalta(ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA,
                recomendador.tendenciasDeZona(sujeto, ubigeo, limite, new LinkedHashSet<>()));
    }

    /* ══════════════ Control del propio perfil ══════════════ */

    /**
     * «Tus intereses»: lo que el sistema cree saber de quien pregunta.
     *
     * <p>Cubre el derecho de acceso de la Ley 29733 y, sobre todo, convierte un
     * sistema opaco en uno que se puede auditar.
     *
     * <p>Devuelve SOLO el perfil de quien pregunta, y ahora es verdad. Antes
     * este comentario prometía que «no hay forma de pedir el de otro», y para un
     * visitante anónimo era falso: bastaba con mandar su identificador en la
     * cabecera. Lo que lo sostiene no es esta frase, sino que el identificador
     * ya no se acepta sin la firma que solo el servidor sabe calcular; quien
     * mande uno ajeno sin ella recibe un sujeto nuevo y vacío.
     */
    @GetMapping("/mis-intereses")
    public List<InteresResponse> misIntereses(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, firmaDelCliente, null);
        return perfiles.completo(sujeto).stream()
                .map(f -> new InteresResponse(
                        f.getId().getTipoFaceta().name(),
                        f.getId().getFaceta(),
                        f.getScore().doubleValue(),
                        f.getEventos()))
                .toList();
    }

    /** Derecho de cancelación: borra el perfil de intereses de quien lo pide. */
    @DeleteMapping("/mis-intereses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void olvidarme(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestHeader(value = CABECERA_FIRMA, required = false) String firmaDelCliente,
            @AuthenticationPrincipal Jwt jwt) {

        perfiles.olvidar(resolver(jwt, sujetoDelCliente, firmaDelCliente, null));
    }

    /**
     * Un carrusel vacío en vez de un cuerpo vacío.
     *
     * <p>Devolver {@code null} daba un 200 sin cuerpo, y obligaba a cada
     * cliente —Angular y Flutter— a distinguir «no hay nada» de «se rompió
     * algo». Una lista vacía con su título dice lo mismo sin ambigüedad, y la
     * interfaz decide si oculta la sección.
     */
    private Carrusel vacioSiFalta(ModuloDescubrimiento modulo, Carrusel carrusel) {
        return carrusel != null ? carrusel
                : new Carrusel(modulo.name(), modulo.titulo(null), modulo.origen(), null,
                        List.of());
    }

    /**
     * El sujeto de esta petición.
     *
     * <p>El JWT gana siempre. La cabecera solo se mira cuando no hay cuenta —y
     * aun entonces sirve para fusionar el rastro anónimo, no para suplantar.
     */
    private UUID resolver(Jwt jwt, UUID sujetoDelCliente, String firma, String ubigeo) {
        return sujetos.resolver(JwtUtils.uidDe(jwt), sujetoDelCliente, firma, ubigeo,
                ipCliente.de(peticionActual())).getId();
    }

    /** La petición en curso, para conocer la procedencia sin ensuciar cada firma. */
    private HttpServletRequest peticionActual() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
    }

    /**
     * El cupo de ingesta de ESTE sujeto.
     *
     * <h4>Por qué no bastaba el cupo por IP</h4>
     *
     * <p>El de IP permite ciento veinte escrituras por minuto, y cada una
     * admite cien eventos: doce mil eventos por minuto contra un mismo perfil.
     * El daño de envenenar un perfil se mide por identidad, no por procedencia,
     * así que el límite tiene que estar también ahí.
     *
     * <p>La clave vive solo en memoria del limitador. No se registra, no se
     * etiqueta ninguna métrica con ella y no sale en ninguna respuesta.
     */
    private void comprobarCupoDe(UUID sujeto) {
        if (!limitador.permitir("sujeto:" + sujeto + "|ingesta",
                pesos.getIngestaPorSujetoPorMinuto(), Duration.ofMinutes(1))) {

            seguridad.rateLimitBloqueado("ingesta-sujeto");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados eventos para este visitante. Inténtalo en un minuto.");
        }
    }

    /** La confirmación de una ingesta, con la firma que el cliente debe guardar. */
    private IngestaResponse respuesta(UUID sujeto, int registrados, int descartados) {
        return new IngestaResponse(sujeto, firmas.de(sujeto), registrados, descartados);
    }
}
