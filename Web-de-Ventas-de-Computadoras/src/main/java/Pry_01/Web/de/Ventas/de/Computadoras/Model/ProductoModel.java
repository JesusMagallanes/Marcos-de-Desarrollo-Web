package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

    @NotBlank
    @Size(min = 2, max = 50)
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @NotBlank
    @Size(min = 10, max = 500)
    @Column(nullable = false, length = 500)
    private String description;

    @Positive
    @Column(nullable = false)
    private double precio;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255, name = "image_url")
    private String imageUrl;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stock;

    @ManyToMany
    @JoinTable(name = "producto_marca", joinColumns = @JoinColumn(name = "producto_id"), inverseJoinColumns = @JoinColumn(name = "marca_id"))
    private List<MarcaModel> marcas = new ArrayList<>();

}
