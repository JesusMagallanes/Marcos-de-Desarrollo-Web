package com.backend.catalogo.atributo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El vocabulario de características del catálogo.
 *
 * <p>Existe porque «Pantalla», «Tamaño de pantalla» y «Display» eran la misma
 * cosa escrita de tres formas dentro de un bloque de texto, y nada podía
 * saberlo. Con un vocabulario, dos productos que declaran {@code pulgadas} son
 * comparables y filtrables; sin él, solo se pueden enseñar.
 *
 * <p>Ver la migración V19 y {@code docs/modelo-datos.md}.
 */
@Entity
@Table(name = "atributo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atributo {

    /** Cómo se guarda y se compara el valor. */
    public enum Tipo {
        TEXTO,
        NUMERO,
        BOOLEANO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Clave natural estable, en minúsculas: {@code pulgadas}, {@code ram_gb}.
     *
     * <p>Es lo que hace comparables dos productos. El {@code nombre} se traduce
     * y se reescribe; esto no.
     */
    @Column(nullable = false, length = 60, unique = true)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    /**
     * La unidad, aparte del valor.
     *
     * <p>Guardar «144 Hz» en el valor devolvería el problema de origen: un
     * número pegado a un texto no se ordena ni se filtra por rango. El número va
     * en el valor y esto solo se enseña.
     */
    @Column(length = 20)
    private String unidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Tipo tipo = Tipo.TEXTO;

    /** Si vale la pena ofrecerlo como filtro en el listado de la categoría. */
    @Column(nullable = false)
    @Builder.Default
    private boolean filtrable = false;
}
