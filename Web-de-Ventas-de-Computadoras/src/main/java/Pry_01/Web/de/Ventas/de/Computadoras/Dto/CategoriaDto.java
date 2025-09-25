package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CategoriaDto {

    @NotNull(message = "El id de la categoría es obligatorio para actualizar.")
    private Long id;

    @NotBlank(message = "El nombre de la categoría es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    private String name;

    @NotBlank(message = "La descripción es obligatoria.")
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres.")
    private String descripcion;

    public CategoriaDto() {
    }

    public CategoriaDto(Long id, String name, String descripcion) {
        this.id = id;
        this.name = name;
        this.descripcion = descripcion;
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
        public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

}
