package com.backend.catalogo.producto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.marca.Marca;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Precio con descuento ya calculado; nulo si el producto no está en oferta. */
    @Column(name = "precio_oferta", precision = 12, scale = 2)
    private BigDecimal precioOferta;

    /** Cómo se calculó el descuento: `PORCENTAJE` o `MONTO`. */
    @Column(name = "descuento_tipo", length = 20)
    private String descuentoTipo;

    /** Valor del descuento: porcentaje (p. ej. 15) o monto en soles (p. ej. 20). */
    @Column(name = "descuento_valor", precision = 12, scale = 2)
    private BigDecimal descuentoValor;

    @Column(name = "oferta_inicio")
    private Instant ofertaInicio;

    @Column(name = "oferta_fin")
    private Instant ofertaFin;

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
    // Sin @Builder.Default el builder ignora este inicializador y deja la lista
    // en null: crear un producto reventaba con NPE al aplicar las imágenes.
    @Builder.Default
    private List<ProductoImagen> imagenes = new ArrayList<>();

    /* ── Dueño y moderación (SZ-B08) ── */

    /**
     * Colaborador que lo publicó. {@code null} = producto de la tienda.
     *
     * <p>Es un id plano y no una relación: el usuario vive en el esquema de otro
     * servicio, y cruzar servicios con una clave foránea es justo lo que este
     * proyecto evita.
     */
    @Column(name = "propietario_id")
    private Long propietarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_moderacion", nullable = false, length = 12)
    @Builder.Default
    private EstadoModeracion estadoModeracion = EstadoModeracion.APROBADO;

    /** Obligatorio al rechazar. El colaborador lo ve para saber qué corregir. */
    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "moderado_por")
    private Long moderadoPor;

    @Column(name = "moderado_en")
    private Instant moderadoEn;

    /** Bloqueo optimista: dos descuentos de stock simultáneos no se pisan. */
    @Version
    @Column(name = "version")
    private Long version;

    /* ── Moderación ── */

    public boolean esDeLaTienda() {
        return propietarioId == null;
    }

    public boolean perteneceA(Long usuarioId) {
        return propietarioId != null && propietarioId.equals(usuarioId);
    }

    /**
     * Lo manda (o lo devuelve) a la cola de revisión.
     *
     * <p>Se llama en cada edición del colaborador, no solo al crearlo. Si no,
     * bastaría con publicar algo inocuo, esperar el visto bueno y cambiarlo
     * después por otra cosa: la moderación no serviría de nada.
     */
    public void enviarAModeracion() {
        this.estadoModeracion = EstadoModeracion.PENDIENTE;
        this.motivoRechazo = null;
        this.moderadoPor = null;
        this.moderadoEn = null;
    }

    public void aprobarModeracion(Long moderadorId) {
        exigirQueSeaModerable();
        this.estadoModeracion = EstadoModeracion.APROBADO;
        this.motivoRechazo = null;
        this.moderadoPor = moderadorId;
        this.moderadoEn = Instant.now();
    }

    public void rechazarModeracion(Long moderadorId, String motivo) {
        exigirQueSeaModerable();
        this.estadoModeracion = EstadoModeracion.RECHAZADO;
        this.motivoRechazo = motivo;
        this.moderadoPor = moderadorId;
        this.moderadoEn = Instant.now();
    }

    /**
     * Lo de la tienda no pasa por revisión. Si se permitiera, el administrador
     * estaría aprobándose a sí mismo y el estado dejaría de significar nada.
     */
    private void exigirQueSeaModerable() {
        if (esDeLaTienda()) {
            throw new IllegalStateException(
                    "Los productos de la tienda no pasan por moderación");
        }
    }

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
