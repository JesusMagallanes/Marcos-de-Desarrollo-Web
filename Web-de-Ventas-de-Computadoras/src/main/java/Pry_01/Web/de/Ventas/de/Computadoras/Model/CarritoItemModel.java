package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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

  
    public CarritoItemModel() {}

    public CarritoItemModel(CarritoModel carrito, ProductoModel producto, Integer cantidad) {
        this.carrito = carrito;
        this.producto = producto;
        this.cantidad = cantidad;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CarritoModel getCarrito() {
        return carrito;
    }

    public void setCarrito(CarritoModel carrito) {
        this.carrito = carrito;
    }

    public ProductoModel getProducto() {
        return producto;
    }

    public void setProducto(ProductoModel producto) {
        this.producto = producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
