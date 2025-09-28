package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "categoria")
public class CategoriaModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la categoría es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    private String UrlImage;

    @NotBlank(message = "La descripción es obligatoria.")
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres.")
    @Column(nullable = false, length = 500)
    private String description;

    @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductoModel> productos = new ArrayList<>();

    public CategoriaModel() {}

    public CategoriaModel(String name, String description, String urlImage) {
        this.name = name;
        this.description = description;
        this.UrlImage = urlImage;
    }

    public void addProducto(ProductoModel producto) {
        productos.add(producto);
        producto.setCategoria(this);
    }

    public void removeProducto(ProductoModel producto) {
        productos.remove(producto);
        producto.setCategoria(null);
    }

    public String getUrlImage() {
        return UrlImage;
    }

    public void setUrlImage(String urlImage) {
        UrlImage = urlImage;
    }
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

    public List<ProductoModel> getProductos() {
        return productos;
    }
    public void setProductos(List<ProductoModel> productos) {
        this.productos = productos;
    }
}
