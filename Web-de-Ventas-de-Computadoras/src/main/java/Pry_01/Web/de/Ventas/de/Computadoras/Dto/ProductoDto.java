package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public class ProductoDto {
    private Long id;

    private String name;

    private String description;

    @Positive(message = "El precio debe ser mayor que 0.")
    private double precio;

    private String imageUrl;

    @Min(value = 0, message = "El stock no puede ser negativo.")
    private Integer stock;

    private Long categoriaId;
    public ProductoDto() {}

    public ProductoDto(Long id, String name, String description, double precio, String imageUrl, Integer stock, Long categoriaId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.precio = precio;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.categoriaId = categoriaId;
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

    public double getPrecio() {
        return precio;
    }

    public void setPrecio(double precio) {
        this.precio = precio;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(Long categoriaId) {
        this.categoriaId = categoriaId;
    }
}