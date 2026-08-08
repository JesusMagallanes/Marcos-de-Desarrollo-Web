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
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.catalogo.CatalogoClient.LineaPrecio;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;
import com.backend.compras.shared.security.TokenActual;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarritoService {

    private final CarritoRepository carritoRepositorio;
    private final CarritoItemRepository itemRepositorio;
    private final CatalogoClient catalogo;

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

    @Transactional
    public CarritoResponse eliminar(Long usuarioId, Long itemId) {
        Carrito carrito = obtenerOCrear(usuarioId);

        CarritoItem item = itemRepositorio.findByIdAndCarritoId(itemId, carrito.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem no está en tu carrito"));

        itemRepositorio.delete(item);
        return construir(obtenerOCrear(usuarioId));
    }

    @Transactional
    public CarritoResponse vaciar(Long usuarioId) {
        Carrito carrito = obtenerOCrear(usuarioId);
        carrito.vaciar();
        carritoRepositorio.save(carrito);
        return new CarritoResponse(List.of(), BigDecimal.ZERO);
    }

    /** Enriquece los items con datos de catálogo y calcula el subtotal. */
    public CarritoResponse construir(Carrito carrito) {
        List<CarritoItem> items = carrito.getItems();
        if (items.isEmpty()) {
            return new CarritoResponse(List.of(), BigDecimal.ZERO);
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

        return new CarritoResponse(respuesta, subtotal);
    }
}
