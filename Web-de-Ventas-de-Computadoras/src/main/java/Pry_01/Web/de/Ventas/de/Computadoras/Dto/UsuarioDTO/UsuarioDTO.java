package Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class UsuarioDTO {
    private Long id;
    private String name;
    private String lastname;
    private String emailAddress;
    private String phoneNumber;
    private String address;
    private String rol;

    public UsuarioDTO(UsuarioModel u) {
        if (u == null) return;
        this.id = u.getId();
        this.name = u.getName();
        this.lastname = u.getLastname();
        this.emailAddress = u.getEmailAddress();
        this.phoneNumber = u.getPhoneNumber();
        this.address = u.getAddress();
        this.rol = u.getRol() != null ? u.getRol().name() : null;
    }
}
