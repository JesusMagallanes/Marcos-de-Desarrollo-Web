package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import jakarta.validation.constraints.Size;

public class MetodoPagoDto {
    
    private Long id;

    @Size(max = 50, message = "El nombre no puede tener más de 50 caracteres.")
    private String name;

    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres.")
    private String description;

    public MetodoPagoDto() {
    }

    public MetodoPagoDto(Long id,String name, String description) {

        this.id=id;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
