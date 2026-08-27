package com.backend.compras.metodopago;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.compras.metodopago.dto.MetodoPagoDtos.MetodoPagoRequest;
import com.backend.compras.metodopago.dto.MetodoPagoDtos.MetodoPagoResponse;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/metodos-pago")
@RequiredArgsConstructor
class MetodoPagoController {

    private final MetodoPagoService servicio;

    @GetMapping
    public List<MetodoPagoResponse> listar() {
        return servicio.listar();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_METODOS_PAGO_GESTIONAR')")
    public ResponseEntity<MetodoPagoResponse> crear(@Valid @RequestBody MetodoPagoRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_METODOS_PAGO_GESTIONAR')")
    public MetodoPagoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MetodoPagoRequest dto) {
        return servicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_METODOS_PAGO_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MetodoPagoService {

    private final MetodoPagoRepository repositorio;

    List<MetodoPagoResponse> listar() {
        return repositorio.findAllByOrderByIdAsc().stream().map(MetodoPagoResponse::desde).toList();
    }

    @Transactional
    MetodoPagoResponse crear(MetodoPagoRequest dto) {
        if (repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe un método de pago con ese nombre");
        }
        MetodoPago metodo = MetodoPago.builder()
                .name(dto.name())
                .description(dto.description())
                .tipo(dto.tipo())
                .build();
        return MetodoPagoResponse.desde(repositorio.save(metodo));
    }

    @Transactional
    MetodoPagoResponse actualizar(Long id, MetodoPagoRequest dto) {
        MetodoPago metodo = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Método de pago " + id + " no encontrado"));

        if (!metodo.getName().equals(dto.name()) && repositorio.existsByName(dto.name())) {
            throw new ConflictoException("Ya existe un método de pago con ese nombre");
        }

        metodo.setName(dto.name());
        metodo.setDescription(dto.description());
        metodo.setTipo(dto.tipo());
        return MetodoPagoResponse.desde(repositorio.save(metodo));
    }

    @Transactional
    void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Método de pago " + id + " no encontrado");
        }
        repositorio.deleteById(id);
    }
}
