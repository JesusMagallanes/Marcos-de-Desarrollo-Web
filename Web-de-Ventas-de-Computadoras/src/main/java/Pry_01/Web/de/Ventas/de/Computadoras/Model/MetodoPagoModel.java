package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "metodopago")
public class MetodoPagoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre del método de pago no puede estar vacío.")
    @Size(max = 50, message = "El nombre no puede tener más de 50 caracteres.")
    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @NotBlank(message = "La descripción no puede estar vacía.")
    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres.")
    @Column(nullable = false, length = 200)
    private String description;

}