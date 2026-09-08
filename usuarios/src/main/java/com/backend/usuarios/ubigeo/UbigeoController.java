package com.backend.usuarios.ubigeo;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.usuarios.shared.error.RecursoNoEncontradoException;
import com.backend.usuarios.shared.validacion.Saneador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * El catálogo de departamentos, provincias y distritos del Perú.
 *
 * <p>Alimenta los tres desplegables en cascada del formulario de dirección. Es
 * público a propósito: son datos oficiales del INEI, iguales para todo el mundo,
 * y pedir sesión para pintar una lista de 25 departamentos solo estorbaría en el
 * registro.
 *
 * <p>Solo lectura. Lo que cambia esta tabla es una migración.
 */
@RestController
@RequestMapping("/api/ubigeo")
@RequiredArgsConstructor
@Validated
@Transactional(readOnly = true)
public class UbigeoController {

    private final UbigeoRepository repositorio;

    @GetMapping("/departamentos")
    public List<String> departamentos() {
        return repositorio.departamentos();
    }

    @GetMapping("/provincias")
    public List<String> provincias(
            @RequestParam @NotBlank @Size(max = 80) String departamento) {
        return repositorio.provinciasDe(Saneador.texto(departamento));
    }

    @GetMapping("/distritos")
    public List<String> distritos(
            @RequestParam @NotBlank @Size(max = 80) String departamento,
            @RequestParam @NotBlank @Size(max = 80) String provincia) {
        return repositorio.distritosDe(Saneador.texto(departamento), Saneador.texto(provincia));
    }

    /**
     * El codigo INEI de un distrito.
     *
     * <p>Los tres desplegables devuelven NOMBRES, que es lo que necesita el
     * formulario de direccion. Pero el descubrimiento razona por codigo: la
     * degradacion geografica —distrito, provincia, departamento, nacional— es
     * recortar los seis digitos, y con nombres sueltos no se puede hacer.
     *
     * <p>Devuelve 404 si la combinacion no existe. Que los tres nombres viajen
     * por la red no garantiza que formen un distrito real: nada impide pedir
     * «Miraflores» dentro de un departamento donde no esta.
     */
    @GetMapping("/codigo")
    public UbigeoCodigo codigo(
            @RequestParam @NotBlank @Size(max = 80) String departamento,
            @RequestParam @NotBlank @Size(max = 80) String provincia,
            @RequestParam @NotBlank @Size(max = 80) String distrito) {

        return repositorio.buscar(Saneador.texto(departamento), Saneador.texto(provincia),
                        Saneador.texto(distrito))
                .map(u -> new UbigeoCodigo(u.getCodigo(), u.getDepartamento(), u.getProvincia(),
                        u.getDistrito()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el distrito %s / %s / %s".formatted(
                                departamento, provincia, distrito)));
    }

    /** El codigo y los nombres ya normalizados como estan en la tabla. */
    public record UbigeoCodigo(String codigo, String departamento, String provincia,
            String distrito) {
    }
}
