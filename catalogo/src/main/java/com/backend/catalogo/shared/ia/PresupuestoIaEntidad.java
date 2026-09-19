package com.backend.catalogo.shared.ia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La fila del contador de llamadas al modelo.
 *
 * <p>Existe para que el repositorio sea un {@code JpaRepository} de verdad y la
 * tabla esté declarada donde se declara todo lo demás. Quien cuenta es el UPSERT
 * de {@link PresupuestoIaRepository}: nadie guarda esta entidad a mano, porque
 * leerla y volver a escribirla desde Java es justo la carrera que se evita.
 */
@Entity
@Table(name = "ia_presupuesto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoIaEntidad {

    /** El ámbito del tope; hoy solo {@code global}. */
    @Id
    @Column(length = 40)
    private String clave;

    /** Minuto epoch al que corresponde la cuenta. */
    @Column(nullable = false)
    private long minuto;

    @Column(nullable = false)
    private int usadas;
}
