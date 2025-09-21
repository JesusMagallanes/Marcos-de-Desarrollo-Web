package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categoria")
public class CategoriaController {
     private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<CategoriaModel> listarCategoria() {
        return categoriaService.listarCategoria();
    }

    @PostMapping
    public ResponseEntity<CategoriaModel> crearCategoria(@Valid @RequestBody CategoriaModel categoria) {
        CategoriaModel nuevo = categoriaService.guardarCategoria(categoria);
        return ResponseEntity.ok(nuevo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }

    /*@PutMapping("/{id}") */
    
}
