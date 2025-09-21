package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public class UsuarioDto {
    private Long id;
    private String name;

    private String lastname;
    
    @Email
    private String emailAddress;

    @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener exactamente 9 dígitos.")
    private String phoneNumber;

    private String address;

    private Roles rol;

    public UsuarioDto() {
    }

    public UsuarioDto(Long id, String name, String lastname, String emailAddress, String phoneNumber, String address, Roles rol) {
        this.id = id;
        this.name = name;
        this.lastname = lastname;
        this.emailAddress = emailAddress;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.rol = rol;
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

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Roles getRol() {
        return rol;
    }

    public void setRol(Roles rol) {
        this.rol = rol;
    }
}