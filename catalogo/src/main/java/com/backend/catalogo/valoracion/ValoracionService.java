package com.backend.catalogo.valoracion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionAdminResponse;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionRequest;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ValoracionService {

    private final ValoracionRepository repositorio;
    private final ProductoRepository productoRepository;

    /** Las reseñas que ve la tienda: solo las aprobadas por un administrador. */
    public List<ValoracionResponse> listar(Long productoId) {
        verificarProducto(productoId);
        return repositorio.findByProductoIdAndEstadoOrderByCreadoEnDesc(
                        productoId, EstadoValoracion.APROBADA)
                .stream()
                .map(ValoracionResponse::desde)
                .toList();
    }

    /**
     * La valoración del usuario en curso, o null si aún no valora. Devuelve la
     * suya sin filtrar por estado: si está pendiente de revisión o rechazada,
     * el cliente debe poder verla y saber en qué fase está.
     */
    public ValoracionResponse obtenerMia(Long productoId, Long usuarioId) {
        return repositorio.findByProductoIdAndUsuarioId(productoId, usuarioId)
                .map(ValoracionResponse::desde)
                .orElse(null);
    }

    /**
     * Una valoración por cliente y producto: si el cliente ya valoró, la
     * existente se actualiza en vez de duplicarse. Cualquier envío (nuevo o
     * revaloración) vuelve a quedar PENDIENTE: el texto cambió, hay que
     * revisarlo otra vez.
     */
    @Transactional
    public ValoracionResponse guardar(Long productoId, Long usuarioId, ValoracionRequest dto) {
        var producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + productoId + " no encontrado"));

        Valoracion valoracion = repositorio.findByProductoIdAndUsuarioId(productoId, usuarioId)
                .orElseGet(() -> Valoracion.builder()
                        .producto(producto)
                        .usuarioId(usuarioId)
                        .creadoEn(Instant.now())
                        .build());

        valoracion.setNombre(dto.nombre().trim());
        valoracion.setCalificacion(dto.calificacion());
        valoracion.setComentario(dto.comentario().trim());
        valoracion.setEstado(EstadoValoracion.PENDIENTE);
        valoracion.setActualizadoEn(Instant.now());

        return ValoracionResponse.desde(repositorio.save(valoracion));
    }

    @Transactional
    public void eliminar(Long productoId, Long usuarioId) {
        Valoracion valoracion = repositorio.findByProductoIdAndUsuarioId(productoId, usuarioId)
                .orElseThrow(() -> new ConflictoException("No tienes una valoración en este producto"));
        repositorio.delete(valoracion);
    }

    /* ── Moderación (ADMINISTRADOR) ── */

    public List<ValoracionAdminResponse> listarParaModeracion(EstadoValoracion estado) {
        List<Valoracion> valoraciones = estado == null
                ? repositorio.findAllByOrderByCreadoEnDesc()
                : repositorio.findByEstadoOrderByCreadoEnDesc(estado);
        return valoraciones.stream()
                .map(ValoracionAdminResponse::desde)
                .toList();
    }

    /** Aprueba, rechaza o devuelve a pendiente una valoración. */
    @Transactional
    public ValoracionAdminResponse cambiarEstado(Long id, EstadoValoracion estado) {
        Valoracion valoracion = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Valoración " + id + " no encontrada"));
        valoracion.setEstado(estado);
        valoracion.setActualizadoEn(Instant.now());
        return ValoracionAdminResponse.desde(repositorio.save(valoracion));
    }

    /** Retira una reseña, por ejemplo abusiva o duplicada. */
    @Transactional
    public void eliminarComoAdmin(Long id) {
        Valoracion valoracion = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Valoración " + id + " no encontrada"));
        repositorio.delete(valoracion);
    }

    /** Promedio y cantidad por producto, para las tarjetas y la cabecera del detalle. */
    public Map<Long, ValoracionRepository.ResumenValoracion> resumenPorProductos(List<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) {
            return Map.of();
        }
        return repositorio.resumenPorProductos(productoIds).stream()
                .collect(Collectors.toMap(
                        ValoracionRepository.ResumenValoracion::getProductoId,
                        Function.identity()));
    }

    private void verificarProducto(Long productoId) {
        if (!productoRepository.existsById(productoId)) {
            throw new RecursoNoEncontradoException("Producto " + productoId + " no encontrado");
        }
    }
}
