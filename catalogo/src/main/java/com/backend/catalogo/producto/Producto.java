package com.backend.catalogo.producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.marca.Marca;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    /** Lista de especificaciones en Markdown, separada del párrafo de descripción. */
    @Column(columnDefinition = "TEXT")
    private String specifications;

    /** BigDecimal, no double: el monolito usaba double para dinero. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private Integer stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    /**
     * Galería de imágenes, ordenada por posición. `imageUrl` sigue guardando la
     * primera (imagen principal) para tarjetas y carrito. `@BatchSize` evita el
     * N+1 en los listados: se carga una consulta por lote, no una por producto.
     */
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicion ASC")
    @BatchSize(size = 100)
    private List<ProductoImagen> imagenes = new ArrayList<>();

    /** Bloqueo optimista: dos descuentos de stock simultáneos no se pisan. */
    @Version
    @Column(name = "version")
    private Long version;

    public void descontarStock(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        if (stock < cantidad) {
            throw new IllegalStateException(
                    "Stock insuficiente para '" + name + "': quedan " + stock);
        }
        this.stock -= cantidad;
    }

    public void reponerStock(int cantidad) {
        if (cantidad > 0) {
            this.stock += cantidad;
        }
    }
}
