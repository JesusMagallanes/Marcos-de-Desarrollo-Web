package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class UsuarioModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length= 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String lastname;

    @Column( unique = true ,nullable = false, length = 100)
    private String emailAddress;

    @Column( name = "password_hash",nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 9)
    private String phoneNumber;

    @Column (nullable = false, length = 200)
    private String address;

    public UsuarioModel(){};
    
    public UsuarioModel(String name, String lastname, String emailAddress, String password, String phoneNumber, String address){
        this.name=name;
        this.lastname=lastname;
        this.emailAddress=emailAddress;
        this.password=password;
        this.phoneNumber=phoneNumber;
        this.address=address;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
}