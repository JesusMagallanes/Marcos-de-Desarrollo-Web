package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.UUID;

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
 * Lo que se le enseñó a alguien, se tocara o no.
 *
 * <p>Registrar solo los clics deja fuera la mitad de la información: sin saber
 * qué se mostró y NO se tocó no se puede calcular el CTR de un módulo, ni
 * dejar de insistir con lo que ya se ignoró tres veces.
 *
 * <p>Tabla propia y no un tipo más de evento, por volumen: un Home pinta unos
 * sesenta ítems y se hace clic en uno.
 */
@Entity
@Table(name = "impresion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Impresion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sujeto_id", nullable = false)
    private UUID sujetoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_tipo", nullable = false, length = 16)
    private TipoItem itemTipo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** El carrusel donde apareció. Es la unidad de medida del recomendador. */
    @Column(nullable = false, length = 40)
    private String modulo;

    @Column
    private Short posicion;

    @Column(name = "con_clic", nullable = false)
    @Builder.Default
    private boolean conClic = false;

    @Column(name = "mostrado_en", nullable = false)
    private Instant mostradoEn;
}
