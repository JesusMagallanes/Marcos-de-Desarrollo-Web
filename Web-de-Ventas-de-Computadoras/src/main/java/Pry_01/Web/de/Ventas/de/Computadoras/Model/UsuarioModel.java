package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class UsuarioModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre no puede estar vacío.")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres.")
    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El nombre solo debe contener letras y espacios")
    @Column(name = "first_name", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "El apellido no puede estar vacío.")
    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El apellido solo debe contener letras y espacios")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastname;

    @NotBlank(message = "El correo no puede estar vacío.")
    @Email(message = "El correo debe tener un formato correcto")
    @Column(unique = true, nullable = false, length = 100)
    private String emailAddress;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "La contraseña no puede estar vacía.")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$", message = "La contraseña debe incluir al menos una mayúscula, una minúscula, un número y un símbolo")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @NotBlank(message = "El teléfono no puede estar vacío.")
    @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener exactamente 9 dígitos.")
    @Column(nullable = false, length = 9)
    private String phoneNumber;

    @NotBlank(message = "La dirección no puede estar vacía.")
    @Column(nullable = false, length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 200)
    private Roles rol;


}