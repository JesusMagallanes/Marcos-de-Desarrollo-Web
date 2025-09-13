package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "detalle_pedido")
public class DetallePedidoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "pedido_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PedidoModel pedido;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false,  precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false,  precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate(){
        this.creadoEn=LocalDateTime.now();
    }
    public LocalDateTime getCreadoEn(){
        return creadoEn;
    }

    public DetallePedidoModel(){}
    public DetallePedidoModel( PedidoModel pedido, ProductoModel producto, Integer cantidad, BigDecimal precioUnitario, BigDecimal total){
        this.pedido=pedido;
        this.producto=producto;
        this.cantidad=cantidad;
        this.precioUnitario=precioUnitario;
        this.total=total;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public PedidoModel getPedido() {
        return pedido;
    }
    public void setPedido(PedidoModel pedido) {
        this.pedido = pedido;
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
    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }
    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }
    public BigDecimal getTotal() {
        return total;
    }
    public void setTotal(BigDecimal total) {
        this.total = precioUnitario.multiply(new BigDecimal(cantidad));
    }
    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
