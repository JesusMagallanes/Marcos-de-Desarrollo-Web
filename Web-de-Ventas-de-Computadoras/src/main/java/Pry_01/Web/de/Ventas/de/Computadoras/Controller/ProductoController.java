package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;
    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    // Guardar producto nuevo
    @PostMapping
    public String guardarProducto(@ModelAttribute("producto") ProductoModel productoModel) {
        productoService.guardarProducto(productoModel);
        return "redirect:/VistaAdmin";
    }

    // Editar producto
    @PostMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id,
                                 @ModelAttribute("producto") ProductoModel producto) {
        producto.setId(id);
        productoService.actualizarProducto(producto);
        return "redirect:/VistaAdmin";
    }

    // Eliminar producto
    @PostMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id) {
        productoService.eliminarProducto(id);
        return "redirect:/VistaAdmin";
    }

    // Obtener producto por ID (para fetch JS si lo usas)
    @GetMapping("/{id}")
    @ResponseBody
    public ProductoModel obtenerProducto(@PathVariable Long id) {
        return productoService.obtenerPorId(id);
    }

    
}
