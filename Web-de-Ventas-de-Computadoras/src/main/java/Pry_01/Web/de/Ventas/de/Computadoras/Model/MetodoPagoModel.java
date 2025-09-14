package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    public MetodoPagoModel() {}

    public MetodoPagoModel(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
}
