package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "carrito")
public class CarritoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El carrito debe estar asociado a un usuario.")
    @ManyToOne(optional = false) 
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UsuarioModel usuario;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    @Size(min = 0, message = "El carrito no puede tener una lista inválida de items.")
    private List<CarritoItemModel> items = new ArrayList<>();

    public CarritoModel() {}

    public CarritoModel(UsuarioModel usuario) {
        this.usuario = usuario;
    }

 
    public void addItem(CarritoItemModel item) {
        items.add(item);
        item.setCarrito(this); 
    }

    public void removeItem(CarritoItemModel item) {
        items.remove(item);
        item.setCarrito(null);
    }


    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UsuarioModel getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioModel usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public List<CarritoItemModel> getItems() {
        return items;
    }

    public void setItems(List<CarritoItemModel> items) {
        this.items = items;
    }
}
