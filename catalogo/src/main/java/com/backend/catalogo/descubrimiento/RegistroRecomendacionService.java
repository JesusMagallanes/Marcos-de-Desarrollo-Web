package com.backend.catalogo.descubrimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deja constancia de qué recomendó el sistema y por qué.
 *
 * <p>Sin esto, ajustar un peso es una corazonada: se cambia un número, algo pasa
 * o no pasa, y no hay forma de atribuirlo. Con esto, cada tarjeta servida queda
 * ligada a su razón, su posición, su score y la configuración exacta que la
 * produjo, y entonces «¿esto mejoró?» tiene respuesta.
 *
 * <h4>No puede romper el Home. Nunca.</h4>
 *
 * <p>Es la misma prioridad que sostiene toda la ingesta: una plataforma que deja
 * de vender porque no pudo escribir una métrica las tiene al revés. Si esta
 * tabla se llena, se bloquea o desaparece, la tienda sigue funcionando y lo
 * único que se pierde es la capacidad de medir, que se recupera sola en la
 * siguiente petición.
 *
 * <h4>Por qué el rodeo del auto-proxy</h4>
 *
 * <p>Cumplir esa promesa resultó ser más sutil de lo que parece, y costó una
 * vuelta. Con el {@code try} DENTRO del método transaccional no basta: el
 * volcado falla, se captura, el método vuelve limpio… y entonces el interceptor
 * intenta confirmar una transacción que Hibernate ya marcó para deshacer, y
 * lanza {@code UnexpectedRollbackException} desde fuera del {@code try}. El
 * Home se caía igual.
 *
 * <p>La captura tiene que estar FUERA del límite transaccional, y para eso la
 * llamada debe atravesar el proxy. De ahí el {@code ObjectProvider}: es el mismo
 * apaño que ya usan {@code CheckoutOrquestador.compensar} y
 * {@code AuthService.cortarSesiones} por la misma razón.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroRecomendacionService {

    private final RecomendacionServidaRepository servidas;
    private final MetricasDescubrimiento metricas;
    private final PesosDescubrimiento pesos;

    /** Uno mismo, pero pasando por el proxy. Ver el encabezado. */
    private final ObjectProvider<RegistroRecomendacionService> self;

    /**
     * Anota un carrusel entero, pase lo que pase.
     *
     * @param razonPorItem razón concreta de cada ítem cuando el módulo mezcla
     *     varias —el colaborativo lo hace—; si falta, se usa la del módulo
     * @param scorePorItem score final de cada ítem; los módulos que no producen
     *     un score comparable no lo aportan, y eso es mejor que inventar un
     *     número que luego se compararía con los de verdad
     */
    public void anotar(UUID sujeto, ModuloDescubrimiento modulo, List<Long> itemsEnOrden,
            Map<Long, RazonRecomendacion> razonPorItem,
            Map<Long, Double> scorePorItem,
            boolean conPerfil) {

        if (sujeto == null || itemsEnOrden.isEmpty()) {
            return;
        }
        try {
            self.getObject().guardar(sujeto, modulo, itemsEnOrden,
                    razonPorItem, scorePorItem, conPerfil);

            // Solo si llego a escribirse: contar lo que fallo daria una tasa de
            // exito inventada justo cuando hace falta que sea de verdad.
            for (Long item : itemsEnOrden) {
                metricas.recomendacionesServidas(
                        razonPorItem.getOrDefault(item, modulo.razonPorDefecto()), 1);
            }

        } catch (RuntimeException fallo) {
            // A propósito: se registra y se sigue. Ver el encabezado.
            log.warn("No se pudo anotar lo servido del módulo {}: {}",
                    modulo, fallo.getMessage());
        }
    }

    /**
     * La escritura, en su propia transacción.
     *
     * <p>Propia por dos motivos: el Home es {@code readOnly} y desde dentro de
     * él no se podría escribir; y un fallo aquí no debe arrastrar nada de lo que
     * el llamante estuviera haciendo.
     *
     * <p>Público solo porque tiene que pasar por el proxy. No se llama desde
     * fuera: la puerta es {@link #anotar}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(UUID sujeto, ModuloDescubrimiento modulo, List<Long> itemsEnOrden,
            Map<Long, RazonRecomendacion> razonPorItem,
            Map<Long, Double> scorePorItem,
            boolean conPerfil) {

        String version = pesos.rankerVersion();
        Instant ahora = Instant.now();
        List<RecomendacionServida> filas = new ArrayList<>(itemsEnOrden.size());

        for (int i = 0; i < itemsEnOrden.size(); i++) {
            Long item = itemsEnOrden.get(i);
            filas.add(RecomendacionServida.builder()
                    .sujetoId(sujeto)
                    .itemTipo(TipoItem.PRODUCTO)
                    .itemId(item)
                    .modulo(modulo.name())
                    .razon(razonPorItem.getOrDefault(item, modulo.razonPorDefecto()))
                    .posicion((short) i)
                    .score(BigDecimal.valueOf(scorePorItem.getOrDefault(item, 0.0))
                            .setScale(6, RoundingMode.HALF_UP))
                    .rankerVersion(version)
                    .conPerfil(conPerfil)
                    .servidoEn(ahora)
                    .build());
        }
        servidas.saveAll(filas);
    }
}
