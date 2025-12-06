package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marca")
public class MarcaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la marca es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    @NotBlank(message = "La descripción es obligatoria.")
    private String descripcion;

    // Relación con Categoria
    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaModel categoria;

    // Relación con Producto
    @OneToMany(mappedBy = "marca", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductoModel> productos = new ArrayList<>();

    // Métodos auxiliares
    public void addProducto(ProductoModel producto) {
        productos.add(producto);
        producto.setMarca(this);
    }

    public void removeProducto(ProductoModel producto) {
        productos.remove(producto);
        producto.setMarca(null);
    }
}