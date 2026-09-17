package com.backend.catalogo.descubrimiento;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.PorQueResponse;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.AtributoValor;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

/**
 * «¿Por qué veo esto?» sobre una tarjeta de recomendación.
 *
 * <h4>Las razones se calculan; la IA solo las dice mejor</h4>
 *
 * <p>Las señales son coincidencias comprobables entre el producto y el perfil de
 * QUIEN PREGUNTA: la categoría, la marca o una característica que le ha
 * interesado. Salen de {@code perfil_faceta}, el mismo perfil que ya se puede
 * consultar y borrar en «Tus intereses», así que no se revela nada que la
 * persona no pueda ver por su cuenta. El modelo recibe esas señales y el nombre
 * del producto, y redacta una frase; no inventa razones ni ve el perfil entero.
 *
 * <p>Los módulos colaborativo y de zona nunca dan detalles: decir «alguien de tu
 * distrito con tu mismo gusto miró esto» filtraría conducta ajena. Ahí la
 * explicación es genérica siempre, con o sin IA.
 */
@Service
public class ExplicadorRecomendacion {

    static final String FUNCION = "por_que";
    private static final int SENALES = 3;

    private final PerfilService perfiles;
    private final ProductoService productos;
    private final CategoriaService categorias;
    private final ClienteIa ia;
    private final GuardiaPrompt guardia;

    public ExplicadorRecomendacion(PerfilService perfiles, ProductoService productos,
            CategoriaService categorias, ClienteIa ia, GuardiaPrompt guardia) {
        this.perfiles = perfiles;
        this.productos = productos;
        this.categorias = categorias;
        this.ia = ia;
        this.guardia = guardia;
    }

    public PorQueResponse explicar(UUID sujeto, Long itemId, String modulo) {
        ModuloDescubrimiento m = modulo(modulo);
        ProductoResponse p = productos.obtener(itemId);

        switch (m) {
            case POPULARES:
                return fija("Es de lo más valorado de la tienda y tiene la ficha completa.");
            case LO_MAS_VISTO_EN_TU_ZONA:
                return fija("Es de lo más visto últimamente cerca de ti.");
            case OTROS_DESCUBRIERON:
                return fija("Personas con gustos parecidos a los tuyos también lo han mirado.");
            case DESCUBRE_ALGO_NUEVO:
                return fija("Es distinto de lo que sueles mirar: te lo mostramos para que descubras otras opciones.");
            default:
                break;
        }

        List<String> senales = senales(sujeto, p);
        if (senales.isEmpty()) {
            return fija("Se parece a productos que has estado mirando.");
        }
        String plantilla = "Porque te has interesado por " + senales.stream()
                .map(s -> s.substring(s.indexOf(": ") + 2))
                .collect(Collectors.joining(", ")) + ".";

        GuardiaPrompt.Entrada nombre = guardia.limpiar(p.name(), 150);
        if (!ia.disponible() || nombre.sospechosa()) {
            return new PorQueResponse(plantilla, senales, false);
        }
        String sistema = """
                Explicas a un cliente de SmartZone en UNA frase (máximo 25 palabras, segunda persona, \
                español) por qué le recomendamos un producto. Usa SOLO las señales dadas; no añadas \
                otras razones ni hables de otras personas. Sin markdown.
                %s
                Responde SOLO JSON: {"explicacion": "..."}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = guardia.delimitar("producto", nombre.texto()) + "\n\n"
                + guardia.delimitar("senales", String.join("\n", senales));

        return ia.json(FUNCION, sistema, usuario, 120)
                .map(r -> r.path("explicacion").isString() ? r.path("explicacion").asString().trim() : "")
                .filter(t -> !t.isEmpty() && t.length() <= 220)
                .map(t -> new PorQueResponse(t, senales, true))
                .orElseGet(() -> new PorQueResponse(plantilla, senales, false));
    }

    /**
     * Coincidencias entre el producto y las facetas del perfil, de más a menos peso.
     * Categoría por slug, marca por nombre, atributo por {@code codigo=valor}:
     * las mismas claves con las que {@code PerfilFacetaRepository} las guarda.
     */
    List<String> senales(UUID sujeto, ProductoResponse p) {
        if (sujeto == null) {
            return List.of();
        }
        String slug = categorias.obtener(p.categoriaId()).slug();
        Map<String, AtributoValor> atributos = p.atributos().stream()
                .collect(Collectors.toMap(a -> a.codigo() + "=" + a.valor(), Function.identity(), (a, b) -> a));

        List<String> senales = new ArrayList<>();
        perfiles.completo(sujeto).stream()
                .sorted(Comparator.comparing(PerfilFaceta::getScore).reversed())
                .forEach(f -> {
                    if (senales.size() >= SENALES) {
                        return;
                    }
                    TipoFaceta tipo = f.getId().getTipoFaceta();
                    String valor = f.getId().getFaceta();
                    if (tipo == TipoFaceta.CATEGORIA && valor.equals(slug)) {
                        senales.add("Categoría: " + p.categoriaName());
                    } else if (tipo == TipoFaceta.MARCA && valor.equalsIgnoreCase(p.marcaName())) {
                        senales.add("Marca: " + p.marcaName());
                    } else if (tipo == TipoFaceta.ATRIBUTO && atributos.containsKey(valor)) {
                        AtributoValor a = atributos.get(valor);
                        senales.add("Característica: " + a.nombre() + " " + a.valor());
                    }
                });
        return senales;
    }

    private static ModuloDescubrimiento modulo(String valor) {
        try {
            return ModuloDescubrimiento.valueOf(valor == null ? "" : valor);
        } catch (IllegalArgumentException desconocido) {
            return ModuloDescubrimiento.RELACIONADOS;
        }
    }

    private static PorQueResponse fija(String texto) {
        return new PorQueResponse(texto, List.of(), false);
    }
}
