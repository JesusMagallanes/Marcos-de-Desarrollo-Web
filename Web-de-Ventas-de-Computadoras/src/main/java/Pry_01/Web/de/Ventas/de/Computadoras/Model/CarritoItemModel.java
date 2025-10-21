package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "carrito_item")
public class CarritoItemModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El ítem debe estar asociado a un carrito.")
    @ManyToOne(optional = false)
    @JoinColumn(name = "carrito_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CarritoModel carrito;

    @NotNull(message = "El ítem debe estar asociado a un producto.")
    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto;

    @NotNull(message = "Debe especificar una cantidad.")
    @Min(value = 1, message = "La cantidad mínima es 1.")
    @Column(nullable = false)
    private Integer cantidad;

}