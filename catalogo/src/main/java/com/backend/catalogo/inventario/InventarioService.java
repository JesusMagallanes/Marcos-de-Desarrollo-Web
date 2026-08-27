package com.backend.catalogo.inventario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.producto.dto.ProductoDtos.AjusteStock;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Participante de la saga de compra, del lado del inventario. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventarioService {

    private final ReservaStockRepository reservas;
    private final ProductoRepository productos;

    @Value("${inventario.reserva.minutos:20}")
    private int minutosReserva;

    /**
     * Aparta stock. El descuento sobre `producto.stock` es inmediato: así el catálogo
     * nunca muestra como disponible algo ya comprometido.
     */
    @Transactional
    public void reservar(String referencia, List<AjusteStock> lineas) {
        if (reservas.existsByReferencia(referencia)) {
            log.debug("Reserva {} ya existía; no se repite el descuento", referencia);
            return;
        }

        Instant caducidad = Instant.now().plus(minutosReserva, ChronoUnit.MINUTES);

        for (AjusteStock linea : lineas) {
            // Bloqueo pesimista: dos compras simultáneas de la última unidad se
            // serializan aquí en lugar de descontar ambas.
            Producto producto = productos.buscarParaActualizarStock(linea.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Producto " + linea.productoId() + " no encontrado"));

            if (producto.getStock() < linea.cantidad()) {
                throw new ConflictoException(
                        "Stock insuficiente de '%s': quedan %d".formatted(
                                producto.getName(), producto.getStock()));
            }

            producto.descontarStock(linea.cantidad());
            productos.save(producto);

            reservas.save(ReservaStock.builder()
                    .referencia(referencia)
                    .productoId(linea.productoId())
                    .cantidad(linea.cantidad())
                    .estado(ReservaStock.Estado.ACTIVA)
                    .expiraEn(caducidad)
                    .build());
        }

        log.info("Reserva {} creada con {} líneas, caduca a las {}",
                referencia, lineas.size(), caducidad);
    }

    /** El pago se confirmó: la reserva pasa a definitiva. */
    @Transactional
    public void confirmar(String referencia) {
        List<ReservaStock> lineas = reservas.findByReferencia(referencia);
        if (lineas.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay reserva con referencia " + referencia);
        }

        for (ReservaStock reserva : lineas) {
            if (reserva.getEstado() == ReservaStock.Estado.CONFIRMADA) {
                continue;
            }
            if (!reserva.estaActiva()) {
                // Caducó o se liberó antes de que llegara la confirmación: el
                // stock ya volvió y confirmar ahora lo descuadraría.
                throw new ConflictoException(
                        "La reserva de stock expiró. Vuelve a intentar la compra.");
            }
            reserva.setEstado(ReservaStock.Estado.CONFIRMADA);
            reservas.save(reserva);
        }

        log.info("Reserva {} confirmada", referencia);
    }

    /** Compensación: devuelve el stock apartado. */
    @Transactional
    public void liberar(String referencia) {
        List<ReservaStock> lineas = reservas.findByReferencia(referencia);
        if (lineas.isEmpty()) {
            log.debug("Nada que liberar para la referencia {}", referencia);
            return;
        }

        for (ReservaStock reserva : lineas) {
            if (!reserva.estaActiva()) {
                continue;
            }
            devolverStock(reserva, ReservaStock.Estado.LIBERADA);
        }

        log.info("Reserva {} liberada", referencia);
    }

    /** Barrido de reservas caducadas: el usuario empezó a pagar y no volvió. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirarReservas() {
        List<ReservaStock> caducadas = reservas.buscarCaducadas(Instant.now());
        if (caducadas.isEmpty()) {
            return;
        }

        for (ReservaStock reserva : caducadas) {
            devolverStock(reserva, ReservaStock.Estado.EXPIRADA);
        }
        log.info("Devueltas {} reservas caducadas al stock disponible", caducadas.size());
    }

    private void devolverStock(ReservaStock reserva, ReservaStock.Estado estadoFinal) {
        productos.buscarParaActualizarStock(reserva.getProductoId()).ifPresentOrElse(producto -> {
            producto.reponerStock(reserva.getCantidad());
            productos.save(producto);
        }, () -> log.warn("El producto {} de la reserva {} ya no existe; stock no repuesto",
                reserva.getProductoId(), reserva.getReferencia()));

        reserva.setEstado(estadoFinal);
        reservas.save(reserva);
    }

    /** Traduce el choque del índice único en un mensaje claro para el orquestador. */
    static ConflictoException reservaDuplicada(DataIntegrityViolationException ex) {
        return new ConflictoException("La reserva ya existe para esa referencia");
    }
}
