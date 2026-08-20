package com.backend.usuarios.ubigeo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un distrito del Perú, con la provincia y el departamento a los que pertenece.
 *
 * <p>Es el catálogo oficial del INEI: 25 departamentos, 196 provincias y 1874
 * distritos. Existe para que el comprador ELIJA su distrito en vez de
 * escribirlo: escrito a mano conviven «Miraflores», «miraflores» y «Mirafores»
 * en la misma columna, y con eso no se puede agrupar el reparto por zona ni
 * saber cuánto se vende en cada sitio.
 *
 * <p>Solo lectura. No hay setters ni repositorio de escritura a propósito: lo
 * que cambia esta tabla es una migración, no la aplicación.
 */
@Entity
@Table(name = "ubigeo")
@Getter
@NoArgsConstructor
public class Ubigeo {

    /** Ubigeo de seis dígitos: el identificador oficial del distrito en Perú. */
    @Id
    @Column(length = 6)
    private String codigo;

    @Column(name = "departamento_id", nullable = false, length = 2)
    private String departamentoId;

    @Column(nullable = false, length = 80)
    private String departamento;

    @Column(name = "provincia_id", nullable = false, length = 4)
    private String provinciaId;

    @Column(nullable = false, length = 80)
    private String provincia;

    @Column(nullable = false, length = 80)
    private String distrito;
}
