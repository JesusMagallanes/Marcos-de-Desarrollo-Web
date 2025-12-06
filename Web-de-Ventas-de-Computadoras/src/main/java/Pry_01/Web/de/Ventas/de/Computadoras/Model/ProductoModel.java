package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "producto")
public class ProductoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre no puede estar vacío.")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres.")
    @Column(unique = true ,nullable = false, length = 50)
    private String name;

    @NotBlank(message = "La descripción no puede estar vacía.")
    @Size(min = 10, max = 500, message = "La descripción debe tener entre 10 y 500 caracteres.")
    @Column(nullable = false, length = 500)
    private String description;

    @Positive(message = "El precio debe ser mayor que 0.")
    @Column(nullable = false)
    private double precio;

    @NotBlank(message = "La URL de la imagen no puede estar vacía.")
    @Size(max = 255, message = "La URL de la imagen no debe superar los 255 caracteres.")
    @Column(nullable = false, length = 255, name = "image_url")
    private String imageUrl;

    @NotNull(message = "El stock no puede ser nulo.")
    @Min(value = 0, message = "El stock no puede ser negativo.")
    @Column(nullable = false)
    private Integer stock;

    @ManyToOne
    @JoinColumn(name = "marcaId", nullable = false)
    @NotNull(message = "El producto debe pertenecer a una marca.")
    @JsonIgnoreProperties({"productos", "categoriaId"})
    private MarcaModel marcaId;

    @ManyToOne
    @JoinColumn(name = "categoriaId", nullable = false)
    @NotNull(message = "El producto debe pertenecer a una categoría.")
    @JsonIgnoreProperties({"marcas"})
    private CategoriaModel categoriaId;
}