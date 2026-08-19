package com.backend.usuarios.usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La entidad nunca sale del paquete: los controladores devuelven DTOs. */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastname;

    @Column(name = "email_address", nullable = false, unique = true, length = 100)
    private String emailAddress;

    /** Nulo en cuentas creadas por Google o Facebook: no tienen contraseña. */
    @Column(name = "password_hash", length = 255)
    private String password;

    @Column(name = "phone_number", length = 9)
    private String phoneNumber;

    /**
     * La linea de entrega ya compuesta.
     *
     * <p>No se edita suelta: se deriva de las columnas {@code dir_*}. Antes era
     * texto libre y servia para imprimir una etiqueta y para nada mas — no se
     * puede calcular un envio por codigo postal ni mandarsela a la pasarela.
     */
    @Column(length = 200)
    private String address;

    /* ── Direccion de entrega, en partes ── */

    @Column(name = "dir_calle", length = 200)
    private String dirCalle;

    @Column(name = "dir_numero", length = 20)
    private String dirNumero;

    /** Piso, interior, «el porton verde». Es lo que salva la entrega. */
    @Column(name = "dir_referencia", length = 200)
    private String dirReferencia;

    @Column(name = "dir_codigo_postal", length = 10)
    private String dirCodigoPostal;

    @Column(name = "dir_distrito", length = 80)
    private String dirDistrito;

    @Column(name = "dir_provincia", length = 80)
    private String dirProvincia;

    @Column(name = "dir_departamento", length = 80)
    private String dirDepartamento;

    /** ISO 3166-1 alfa-2. */
    @Column(name = "dir_pais", length = 2)
    private String dirPais;

    @Column(name = "dir_latitud", precision = 9, scale = 6)
    private BigDecimal dirLatitud;

    @Column(name = "dir_longitud", precision = 9, scale = 6)
    private BigDecimal dirLongitud;

    /** Sin esto no se puede entregar, y es lo que decide si se puede comprar. */
    public boolean tieneDireccionCompleta() {
        return dirCalle != null && !dirCalle.isBlank()
                && dirNumero != null && !dirNumero.isBlank()
                && dirCodigoPostal != null && !dirCodigoPostal.isBlank()
                && dirDistrito != null && !dirDistrito.isBlank()
                && dirProvincia != null && !dirProvincia.isBlank()
                && dirDepartamento != null && !dirDepartamento.isBlank();
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Proveedor proveedor = Proveedor.LOCAL;

    /** Nombre del rol (clave natural de la tabla `rol`); viaja en el JWT. */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String rol = "CLIENTE";

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (rol == null) {
            rol = "CLIENTE";
        }
        if (proveedor == null) {
            proveedor = Proveedor.LOCAL;
        }
    }

    /** Una cuenta de Google o Facebook no puede entrar por el formulario. */
    public boolean puedeIniciarSesionConPassword() {
        return proveedor.permiteLoginConPassword() && password != null;
    }
}
