package com.backend.catalogo.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.ia.dto.IaDtos.RedaccionRequest;
import com.backend.catalogo.ia.dto.IaDtos.RedaccionSugerida;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.producto.FiltroProductos;
import com.backend.catalogo.producto.FiltroProductos.Orden;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.FacetaAtributo;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Un borrador de descripción y especificaciones a partir del nombre del producto.
 *
 * <p>Es lo que más tiempo le cuesta a quien publica —sobre todo al colaborador,
 * que sube su catálogo a mano— y lo que más se nota cuando falta: una ficha
 * vacía no se encuentra por atributos ni convence a nadie.
 *
 * <h4>Borrador, y con el vocabulario de la tienda</h4>
 *
 * <p>La respuesta rellena el formulario y la persona la revisa antes de guardar:
 * nada se publica desde aquí, y lo de un colaborador pasa además por
 * moderación. Las especificaciones se piden con los NOMBRES de atributo que ya
 * usa esa categoría («Pantalla», no «Display»), que es lo que las hace
 * filtrables en lugar de otro bloque de texto con palabras distintas.
 *
 * <h4>El formato se arregla antes de descartar</h4>
 *
 * <p>El campo del formulario espera {@code · **Nombre**: valor}, y un modelo
 * escribe lo mismo de cinco maneras: {@code - Nombre: valor}, {@code Nombre:
 * valor}, con viñeta pero sin negrita… Descartar todo lo que no encajara al pie
 * de la letra dejaba el campo vacío sin decir por qué, que para quien publica es
 * indistinguible de «la IA no funciona». Así que primero se normaliza cada línea
 * y solo se tira lo que ni siquiera es «nombre: valor». Lo descartado se cuenta
 * en {@code smartzone_ia_lineas_descartadas_total}: si sube, el prompt o el
 * modelo dejaron de entenderse y se ve antes de que lo reporte un vendedor.
 */
@Service
public class RedactorFichas {

    static final String FUNCION = "redaccion";

    /** El formato del campo de especificaciones: {@code · **Nombre**: valor}. */
    private static final Pattern LINEA = Pattern.compile("^· \\*\\*[^*\\n]{1,60}\\*\\*: [^\\n]{1,140}$");

    /** Lo mínimo para poder reconstruir la línea: un nombre, dos puntos y un valor. */
    private static final Pattern NOMBRE_VALOR = Pattern.compile(
            "^(?:[-*·•]\\s*)?\\**\\s*([^*:\\n]{1,60}?)\\s*\\**\\s*:\\s*(.{1,140})$");

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final CategoriaService categorias;
    private final MarcaService marcas;
    private final ProductoService productos;
    private final MeterRegistry registro;

    public RedactorFichas(ClienteIa ia, GuardiaPrompt guardia, CategoriaService categorias,
            MarcaService marcas, ProductoService productos, MeterRegistry registro) {
        this.ia = ia;
        this.guardia = guardia;
        this.categorias = categorias;
        this.marcas = marcas;
        this.productos = productos;
        this.registro = registro;
    }

    public Optional<RedaccionSugerida> redactar(RedaccionRequest dto) {
        GuardiaPrompt.Entrada nombre = guardia.limpiar(dto.nombre(), 150);
        GuardiaPrompt.Entrada notas = guardia.limpiar(dto.notas(), 1000);
        if (!ia.disponible() || nombre.vacia() || nombre.sospechosa() || notas.sospechosa()) {
            return Optional.empty();
        }

        CategoriaResponse categoria = categorias.obtener(dto.categoriaId());
        String marca = dto.marcaId() == null ? null : marcas.buscar(dto.marcaId()).getName();
        List<FacetaAtributo> vocabulario = productos.facetas(null, categoria.slug(),
                new FiltroProductos(null, null, List.of(), List.of(), false, Orden.RELEVANCIA)).atributos();

        String sistema = """
                Redactas fichas de producto para SmartZone, tienda de tecnología de Perú.
                Escribe en español neutro, claro y sin exageraciones de marketing.

                Nombres de característica que ya usa esta categoría (úsalos tal cual cuando apliquen):
                %s

                Reglas:
                - Solo afirma especificaciones que sean ciertas para ESE modelo exacto. Si no estás \
                seguro de un dato, omítelo: es mejor una ficha corta que un dato falso.
                - No menciones precio, stock, garantía, envío ni promociones.
                - "descripcion": 2 o 3 frases, máximo 450 caracteres, sin viñetas.
                - "especificaciones": entre 3 y 10 líneas, cada una EXACTAMENTE con el formato \
                · **Nombre**: valor
                %s

                Responde SOLO JSON: {"descripcion": "...", "especificaciones": "línea\\nlínea"}
                """.formatted(
                vocabulario.isEmpty() ? "(ninguno todavía)"
                        : vocabulario.stream().map(FacetaAtributo::nombre).distinct()
                                .collect(Collectors.joining(", ")),
                GuardiaPrompt.REGLA_DATOS);

        String usuario = "Categoría: " + categoria.name()
                + (marca == null ? "" : "\nMarca: " + marca) + "\n\n"
                + guardia.delimitar("nombre", nombre.texto())
                + (notas.vacia() ? "" : "\n\n" + guardia.delimitar("notas_del_vendedor", notas.texto()));

        return ia.json(FUNCION, sistema, usuario, 700).flatMap(r -> {
            String descripcion = Respuestas.texto(r, "descripcion", 490);
            String especificaciones = especificaciones(r.path("especificaciones").asString(""));
            return descripcion.isEmpty() ? Optional.empty()
                    : Optional.of(new RedaccionSugerida(descripcion, especificaciones));
        });
    }

    /** Las líneas que el formulario sabe guardar, normalizadas; el resto, a la cuenta. */
    private String especificaciones(String crudas) {
        List<String> buenas = new ArrayList<>();
        int descartadas = 0;
        for (String cruda : crudas.lines().map(String::strip).filter(l -> !l.isEmpty()).toList()) {
            String linea = normalizar(cruda);
            if (linea == null) {
                descartadas++;
            } else if (buenas.size() < 12) {
                buenas.add(linea);
            }
        }
        if (descartadas > 0) {
            registro.counter("smartzone_ia_lineas_descartadas_total", "funcion", FUNCION)
                    .increment(descartadas);
        }
        return String.join("\n", buenas);
    }

    static String normalizar(String linea) {
        if (LINEA.matcher(linea).matches()) {
            return linea;
        }
        Matcher partes = NOMBRE_VALOR.matcher(linea);
        if (!partes.matches()) {
            return null;
        }
        String nombre = partes.group(1).strip();
        String valor = partes.group(2).strip().replace("*", "");
        return nombre.isEmpty() || valor.isEmpty() ? null : "· **" + nombre + "**: " + valor;
    }
}
