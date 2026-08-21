package com.backend.compras.carrito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.compras.carrito.dto.CarritoDtos.AgregarItemRequest;
import com.backend.compras.carrito.dto.CarritoDtos.CarritoResponse;
import com.backend.compras.carrito.dto.CarritoDtos.ItemResponse;
import com.backend.compras.envio.TarifaEnvio;
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.catalogo.CatalogoClient.LineaPrecio;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;
import com.backend.compras.shared.security.TokenActual;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CarritoService {

    private final CarritoRepository carritoRepositorio;
    private final CarritoItemRepository itemRepositorio;
    private final CatalogoClient catalogo;
    private final TarifaEnvio tarifaEnvio;
    private final EntityManager entityManager;

    @Transactional
    public Carrito obtenerOCrear(Long usuarioId) {
        return carritoRepositorio.findByUsuarioId(usuarioId)
                .orElseGet(() -> carritoRepositorio.save(
                        Carrito.builder().usuarioId(usuarioId).build()));
    }

    @Transactional
    public CarritoResponse ver(Long usuarioId) {
        return construir(obtenerOCrear(usuarioId));
    }

    @Transactional
    public CarritoResponse agregar(Long usuarioId, AgregarItemRequest peticion) {
        Carrito carrito = obtenerOCrear(usuarioId);

        // Se valida contra catálogo antes de guardar: no se admiten productos
        // inexistentes ni cantidades por encima del stock.
        LineaPrecio linea = catalogo.precios(TokenActual.valor(), List.of(peticion.productoId())).stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El producto " + peticion.productoId() + " no existe"));

        CarritoItem item = itemRepositorio
                .findByCarritoIdAndProductoId(carrito.getId(), peticion.productoId())
                .orElse(null);

        int cantidadFinal = (item == null ? 0 : item.getCantidad()) + peticion.cantidad();

        if (cantidadFinal > linea.stock()) {
            throw new ConflictoException(
                    "Solo quedan " + linea.stock() + " unidades de '" + linea.nombre() + "'");
        }

        if (item == null) {
            item = CarritoItem.builder()
                    .carrito(carrito)
                    .productoId(peticion.productoId())
                    .cantidad(peticion.cantidad())
                    .build();
        } else {
            item.setCantidad(cantidadFinal);
        }

        itemRepositorio.save(item);
        return construir(obtenerOCrear(usuarioId));
    }

    @Transactional
    public CarritoResponse cambiarCantidad(Long usuarioId, Long itemId, int cantidad) {
        Carrito carrito = obtenerOCrear(usuarioId);

        CarritoItem item = itemRepositorio.findByIdAndCarritoId(itemId, carrito.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem no está en tu carrito"));

        LineaPrecio linea = catalogo.precios(TokenActual.valor(), List.of(item.getProductoId())).stream()
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto ya no está disponible"));

        if (cantidad > linea.stock()) {
            throw new ConflictoException("Solo quedan " + linea.stock() + " unidades disponibles");
        }

        item.setCantidad(cantidad);
        itemRepositorio.save(item);
        return construir(carrito);
    }

    /**
     * Quita una línea del carrito.
     *
     * <p>El borrado es una sentencia explícita y se comprueba cuántas filas
     * tocó. Antes se quitaba el ítem de la colección y se dejaba que el
     * {@code orphanRemoval} de JPA hiciera el resto, que funciona hasta que no
     * funciona: si el DELETE no alcanzaba ninguna fila, nadie se enteraba —el
     * endpoint devolvía 200 y un carrito que ya no incluía el producto, porque
     * la respuesta se construía desde la colección en memoria— y el producto
     * reaparecía al recargar la página. Un borrado que no borra tiene que dar
     * error, no un 200 optimista.
     */
    @Transactional
    public CarritoResponse eliminar(Long usuarioId, Long itemId) {
        Carrito carrito = obtenerOCrear(usuarioId);

        CarritoItem item = itemRepositorio.findByIdAndCarritoId(itemId, carrito.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem no está en tu carrito"));

        int borradas = itemRepositorio.borrarDelCarrito(item.getId(), carrito.getId());
        if (borradas == 0) {
            log.error("El ítem {} del carrito {} no se pudo borrar: la sentencia no tocó ninguna fila",
                    itemId, carrito.getId());
            throw new ConflictoException("No se pudo quitar el producto del carrito. Vuelve a intentarlo.");
        }

        // La colección en memoria todavía tiene el ítem que se acaba de borrar
        // en la base: si se construyera la respuesta con ella, se enseñaría un
        // carrito distinto del que quedó guardado. Se relee.
        return releerCarrito(usuarioId);
    }

    @Transactional
    public CarritoResponse vaciar(Long usuarioId) {
        Carrito carrito = obtenerOCrear(usuarioId);
        itemRepositorio.borrarTodoElCarrito(carrito.getId());
        carrito.vaciar();
        return vacio();
    }

    /**
     * Vuelve a leer el carrito de la base tras un borrado.
     *
     * <p>El {@code clear()} es lo que hace que sea una lectura de verdad: sin
     * él, la consulta devolvería las entidades que JPA ya tiene en memoria,
     * incluida la línea recién borrada, y la respuesta volvería a mentir.
     */
    private CarritoResponse releerCarrito(Long usuarioId) {
        entityManager.flush();
        entityManager.clear();
        return carritoRepositorio.buscarConItems(usuarioId)
                .map(this::construir)
                .orElseGet(this::vacio);
    }

    /** Enriquece los items con datos de catálogo y calcula subtotal, envío y total. */
    public CarritoResponse construir(Carrito carrito) {
        List<CarritoItem> items = carrito.getItems();
        if (items.isEmpty()) {
            return vacio();
        }

        List<Long> ids = items.stream().map(CarritoItem::getProductoId).toList();
        Map<Long, LineaPrecio> porId = new LinkedHashMap<>();
        catalogo.precios(TokenActual.valor(), ids).forEach(l -> porId.put(l.productoId(), l));

        List<ItemResponse> respuesta = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CarritoItem item : items) {
            LineaPrecio linea = porId.get(item.getProductoId());
            if (linea == null) {
                // El producto desapareció del catálogo: se omite en vez de romper
                // la vista del carrito.
                continue;
            }

            BigDecimal parcial = linea.precio().multiply(BigDecimal.valueOf(item.getCantidad()));
            subtotal = subtotal.add(parcial);

            respuesta.add(new ItemResponse(
                    item.getId(),
                    item.getProductoId(),
                    linea.nombre(),
                    linea.precio(),
                    item.getCantidad(),
                    linea.imageUrl(),
                    linea.stock()));
        }

        return conTotales(respuesta, subtotal);
    }

    private CarritoResponse vacio() {
        return conTotales(List.of(), BigDecimal.ZERO);
    }

    private CarritoResponse conTotales(List<ItemResponse> items, BigDecimal subtotal) {
        return new CarritoResponse(items, subtotal,
                tarifaEnvio.para(subtotal), tarifaEnvio.total(subtotal));
    }
}
