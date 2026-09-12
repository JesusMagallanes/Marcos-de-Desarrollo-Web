package com.backend.catalogo.descubrimiento.negocio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.RazonRecomendacion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Categoria;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Concentracion;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Embudo;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.PorBanda;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.PorRazon;
import com.backend.catalogo.descubrimiento.negocio.InformeNegocio.Reparto;

import lombok.RequiredArgsConstructor;

/**
 * Qué comportamiento de negocio ocurre después de exponer una categoría.
 *
 * <h4>Lo que responde y lo que no</h4>
 *
 * <p>Responde: «esta categoría tuvo X exposición, Y interacción y Z acciones de
 * negocio asociadas dentro de la ventana». No responde: «esta categoría vendió
 * gracias al recomendador». La diferencia no es prudencia retórica, es lo único
 * que los datos sostienen — ver {@link InformeNegocio}.
 *
 * <h4>Tres consultas, y ninguna por categoría</h4>
 *
 * <p>El coste no depende de cuántas categorías haya: son tres agregaciones
 * fijas, con el mismo patrón que estrenó el bloque E. Una consulta por categoría
 * sería el N+1 que estas fases tienen prohibido, y aquí además haría que medir
 * costara más cuanto más grande fuera el catálogo, que es lo contrario de lo
 * que se quiere de un instrumento de vigilancia.
 *
 * <h4>La ventana de atribución es la que ya había</h4>
 *
 * <p>{@code descubrimiento.medicion.horas-atribucion}, la misma que usa la
 * agregación diaria. No se estrena otra: una ventana propia daría números más
 * bonitos y haría incomparables dos paneles del mismo sistema.
 */
@Service
@RequiredArgsConstructor
public class NegocioService {

    private final NegocioRepository repositorio;
    private final PesosDescubrimiento pesos;

    @Value("${descubrimiento.medicion.horas-atribucion:24}")
    private int horasAtribucion;

    /**
     * Mide un periodo cerrado por arriba.
     *
     * @param desde inicio, incluido
     * @param hasta fin, EXCLUIDO. Igual que en el bloque E y en la agregación
     *     diaria: con las dos fronteras cerradas, dos periodos consecutivos
     *     contarían dos veces la misma exposición
     * @param modulo un carrusel concreto, o {@code null} para todos
     */
    @Transactional(readOnly = true)
    public InformeNegocio medir(Instant desde, Instant hasta, String modulo) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El periodo tiene que empezar antes de acabar");
        }
        List<Categoria> categorias = categorias(
                repositorio.embudoPorCategoria(desde, hasta, horasAtribucion, modulo));

        return new InformeNegocio(desde, hasta, modulo, horasAtribucion,
                pesos.getMinimoSujetos(),
                categorias,
                bandas(repositorio.embudoPorCategoriaYBanda(
                        desde, hasta, horasAtribucion, modulo)),
                razones(repositorio.embudoPorCategoriaYRazon(
                        desde, hasta, horasAtribucion, modulo)),
                concentracion(categorias));
    }

    private List<Categoria> categorias(List<Object[]> filas) {
        List<Categoria> salida = new ArrayList<>(filas.size());
        for (Object[] f : filas) {
            salida.add(new Categoria(
                    ((Number) f[0]).longValue(),
                    String.valueOf(f[1]),
                    ((Number) f[2]).longValue(),
                    embudoDesde(f, 3)));
        }
        return salida;
    }

    private List<PorBanda> bandas(List<Object[]> filas) {
        List<PorBanda> salida = new ArrayList<>(filas.size());
        for (Object[] f : filas) {
            salida.add(new PorBanda(
                    ((Number) f[0]).longValue(),
                    ((Number) f[1]).shortValue(),
                    embudoDesde(f, 2)));
        }
        return salida;
    }

    private List<PorRazon> razones(List<Object[]> filas) {
        List<PorRazon> salida = new ArrayList<>(filas.size());
        for (Object[] f : filas) {
            salida.add(new PorRazon(
                    ((Number) f[0]).longValue(),
                    String.valueOf(f[1]),
                    RazonRecomendacion.valueOf(String.valueOf(f[2])),
                    String.valueOf(f[3]),
                    Boolean.TRUE.equals(f[4]),
                    embudoDesde(f, 5)));
        }
        return salida;
    }

    /** Las siete cuentas del embudo, que llegan siempre seguidas y en el mismo orden. */
    private Embudo embudoDesde(Object[] fila, int desplazamiento) {
        return new Embudo(
                ((Number) fila[desplazamiento]).longValue(),
                ((Number) fila[desplazamiento + 1]).longValue(),
                ((Number) fila[desplazamiento + 2]).longValue(),
                ((Number) fila[desplazamiento + 3]).longValue(),
                ((Number) fila[desplazamiento + 4]).longValue(),
                ((Number) fila[desplazamiento + 5]).longValue(),
                ((Number) fila[desplazamiento + 6]).longValue());
    }

    /**
     * Dónde se acumulan clics, carritos y compras.
     *
     * <p>Cada etapa por separado: la concentración de clics y la de carritos
     * pueden estar en categorías distintas, y saberlo es la mitad del valor. Un
     * único reparto las promediaría y borraría esa señal.
     */
    private Concentracion concentracion(List<Categoria> categorias) {
        if (categorias.isEmpty()) {
            return Concentracion.vacia();
        }
        return new Concentracion(
                reparto(categorias, c -> c.embudo().clics()),
                reparto(categorias, c -> c.embudo().carritos()),
                reparto(categorias, c -> c.embudo().compras()));
    }

    private Reparto reparto(List<Categoria> categorias,
            java.util.function.ToLongFunction<Categoria> etapa) {

        long total = categorias.stream().mapToLong(etapa).sum();
        if (total == 0) {
            /*
             * Sin total no hay reparto, y los `null` lo dicen. Devolver 0,0
             * seria afirmar que la concentracion se midio y salio nula, cuando
             * lo que pasa es que no hubo nada que repartir.
             */
            return Reparto.vacio();
        }
        List<Long> orden = categorias.stream()
                .map(c -> etapa.applyAsLong(c))
                .sorted(Comparator.reverseOrder())
                .toList();

        long primera = orden.get(0);
        long cinco = orden.stream().limit(5).mapToLong(Long::longValue).sum();

        return new Reparto(total, (double) primera / total, (double) cinco / total);
    }
}
