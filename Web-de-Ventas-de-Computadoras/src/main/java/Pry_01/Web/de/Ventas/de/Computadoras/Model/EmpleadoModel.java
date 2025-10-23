package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "empleado")
public class EmpleadoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 200)
    private CargoEmpleado cargo = CargoEmpleado.SIN_ASIGNAR;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;


    public EmpleadoModel(){}

    public EmpleadoModel(UsuarioModel usuario){
        this.cargo = cargo != null  ? cargo : CargoEmpleado.SIN_ASIGNAR;
        this.usuario = usuario;
    }
    
    public CargoEmpleado getcargo() {
        return cargo;
    }

    public void setCargo(CargoEmpleado cargo) {
        this.cargo = cargo;
    
    }
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UsuarioModel getUsuario(){
        return usuario;
    }
    
    public void setUsuario(UsuarioModel usuario){
        this.usuario = usuario;
    }
}
