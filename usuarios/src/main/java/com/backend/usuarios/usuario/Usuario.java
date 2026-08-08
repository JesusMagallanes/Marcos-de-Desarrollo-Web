package com.backend.usuarios.usuario;

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

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Proveedor proveedor = Proveedor.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Rol rol = Rol.CLIENTE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (rol == null) {
            rol = Rol.CLIENTE;
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
