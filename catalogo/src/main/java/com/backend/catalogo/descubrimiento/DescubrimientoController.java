package com.backend.catalogo.descubrimiento;

import java.util.LinkedHashSet;
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

import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.HomeResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.IngestaResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.InteresResponse;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteEventosRequest;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.LoteImpresionesRequest;
import com.backend.catalogo.shared.seguridad.JwtUtils;

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

    private static final int MAXIMO_POR_CARRUSEL = 24;

    private final SujetoService sujetos;
    private final IngestaService ingesta;
    private final RecomendacionService recomendador;
    private final PerfilService perfiles;

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
            /*
             * Alternativa a la cabecera, solo para `navigator.sendBeacon`.
             *
             * Al ocultarse la pestaña el navegador ya no permite una peticion
             * normal —se cancelaria y se perderian los eventos de la ultima
             * pantalla, que suele ser la mas interesante—, y sendBeacon no
             * admite cabeceras. Vale exactamente lo mismo que `X-Sujeto`: sigue
             * mandando el JWT si lo hay, asi que no abre ninguna via de
             * suplantacion que la cabecera no abriera ya.
             */
            @RequestParam(value = "sujeto", required = false) UUID sujetoEnQuery,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt,
                sujetoDelCliente != null ? sujetoDelCliente : sujetoEnQuery, lote.ubigeo());
        return new IngestaResponse(sujeto, ingesta.registrar(sujeto, lote));
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
            /* Igual que en /eventos: sendBeacon no puede mandar cabeceras. */
            @RequestParam(value = "sujeto", required = false) UUID sujetoEnQuery,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt,
                sujetoDelCliente != null ? sujetoDelCliente : sujetoEnQuery, null);
        return new IngestaResponse(sujeto,
                ingesta.registrarImpresiones(sujeto, lote.impresiones()));
    }

    /* ══════════════ Lectura ══════════════ */

    /** El Home entero, ya de-duplicado entre carruseles. */
    @GetMapping("/home")
    public HomeResponse home(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "El ubigeo son seis dígitos") String ubigeo,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int porCarrusel,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, ubigeo);
        return new HomeResponse(sujeto, recomendador.home(sujeto, ubigeo, porCarrusel));
    }

    @GetMapping("/segun-intereses")
    public Carrusel segunIntereses(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, null);
        return vacioSiFalta(ModuloDescubrimiento.SEGUN_TUS_INTERESES,
                recomendador.segunIntereses(sujeto, limite, new LinkedHashSet<>()));
    }

    /**
     * Relacionados con un producto.
     *
     * <p>No necesita sujeto: se resuelve por contenido, así que funciona igual
     * para un visitante que llega desde un buscador y no ha hecho nada todavía.
     */
    @GetMapping("/similares/{itemId}")
    public Carrusel similares(
            @PathVariable @Positive Long itemId,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite) {
        return vacioSiFalta(ModuloDescubrimiento.RELACIONADOS,
                recomendador.similares(itemId, limite));
    }

    @GetMapping("/tendencias")
    public Carrusel tendencias(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9]{6}$", message = "El ubigeo son seis dígitos") String ubigeo,
            @RequestParam(defaultValue = "12") @Min(1) @Max(MAXIMO_POR_CARRUSEL) int limite,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, ubigeo);
        return vacioSiFalta(ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA,
                recomendador.tendenciasDeZona(sujeto, ubigeo, limite, new LinkedHashSet<>()));
    }

    /* ══════════════ Control del propio perfil ══════════════ */

    /**
     * «Tus intereses»: lo que el sistema cree saber de quien pregunta.
     *
     * <p>Cubre el derecho de acceso de la Ley 29733 y, sobre todo, convierte un
     * sistema opaco en uno que se puede auditar. Devuelve SOLO el perfil del
     * sujeto que hace la petición; no hay forma de pedir el de otro.
     */
    @GetMapping("/mis-intereses")
    public List<InteresResponse> misIntereses(
            @RequestHeader(value = "X-Sujeto", required = false) UUID sujetoDelCliente,
            @AuthenticationPrincipal Jwt jwt) {

        UUID sujeto = resolver(jwt, sujetoDelCliente, null);
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
            @AuthenticationPrincipal Jwt jwt) {

        perfiles.olvidar(resolver(jwt, sujetoDelCliente, null));
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
    private UUID resolver(Jwt jwt, UUID sujetoDelCliente, String ubigeo) {
        return sujetos.resolver(JwtUtils.uidDe(jwt), sujetoDelCliente, ubigeo).getId();
    }
}
