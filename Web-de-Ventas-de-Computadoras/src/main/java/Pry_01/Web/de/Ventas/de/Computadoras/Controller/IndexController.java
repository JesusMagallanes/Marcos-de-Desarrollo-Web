package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import jakarta.servlet.http.HttpSession;

@Controller
public class IndexController {
    private final CategoriaService categoriaService;
    private final ProductoService productoService;

    // Inyección por constructor (recomendada)
    public IndexController(CategoriaService categoriaService, ProductoService productoService) {
        this.categoriaService = categoriaService;
        this.productoService = productoService;
    }

    @ModelAttribute
    public void addUsuarioToModel(HttpSession session, Model model) {
        var usuario = session.getAttribute("usuario");
        if (usuario != null) {
            model.addAttribute("usuario", usuario);
        }
    }

    // Página principal ("/")
    @GetMapping({ "/", "/Index" })
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        List<ProductosResponseDTO> productos = productoService.listarProducto();
        model.addAttribute("categorias", categorias);
        model.addAttribute("productos", productos);
        // Agrupar productos por categoría para un renderizado más sencillo en la vista
        java.util.Map<Long, java.util.List<ProductosResponseDTO>> productosPorCategoria = new java.util.LinkedHashMap<>();
        for (CategoriaModel cat : categorias) {
            java.util.List<ProductosResponseDTO> filt = productos.stream()
                    .filter(p -> p.getCategoriaName() != null && p.getCategoriaName().equals(cat.getName()))
                    .toList();
            productosPorCategoria.put(cat.getId(), filt);
        }
        model.addAttribute("productosPorCategoria", productosPorCategoria);

        // Crear versiones particionadas (chunks) de tamaño 6 por categoría para las
        // slides del carrusel (desktop)
        java.util.Map<Long, java.util.List<java.util.List<ProductosResponseDTO>>> productosPorCategoriaChunks = new java.util.LinkedHashMap<>();
        int chunkSize = 6;
        for (java.util.Map.Entry<Long, java.util.List<ProductosResponseDTO>> entry : productosPorCategoria.entrySet()) {
            java.util.List<ProductosResponseDTO> list = entry.getValue();
            java.util.List<java.util.List<ProductosResponseDTO>> chunks = new java.util.ArrayList<>();
            if (list != null && !list.isEmpty()) {
                for (int i = 0; i < list.size(); i += chunkSize) {
                    int end = Math.min(list.size(), i + chunkSize);
                    chunks.add(new java.util.ArrayList<>(list.subList(i, end)));
                }
            }
            productosPorCategoriaChunks.put(entry.getKey(), chunks);
        }
        model.addAttribute("productosPorCategoriaChunks", productosPorCategoriaChunks);
        // Crear chunks para la sección "Productos Top" (promo + 5 productos por slide)
        int topChunkSize = 5; // 5 productos por slide + 1 promo fijo
        java.util.List<java.util.List<ProductosResponseDTO>> productosTopChunks = new java.util.ArrayList<>();
        if (productos != null && !productos.isEmpty()) {
            for (int i = 0; i < productos.size(); i += topChunkSize) {
                int end = Math.min(productos.size(), i + topChunkSize);
                productosTopChunks.add(new java.util.ArrayList<>(productos.subList(i, end)));
            }
        }
        model.addAttribute("productosTopChunks", productosTopChunks);
        return "Index";
    }

    @GetMapping("/EnviosPag")
    public String enviosPag() {
        return "redirect:/envios";
    }

    @GetMapping("/header")
    public String mostrarHeader() {
        // Si usas Thymeleaf y quieres el fragmento concreto: "fragments/headerFooter ::
        // header"
        // return "fragments/headerFooter :: header";
        return "fragments/headerFooter/header";
    }

    @GetMapping("/Carrito")
    public String mostrarCarrito() {
        return "Carrito";
    }

    @GetMapping("/Somos")
    public String mostrarSomos() {
        return "Somos";
    }

    @GetMapping("/canales")
    public String mostrarCanales() {
        return "Canales";
    }

    @GetMapping("/Canales")
    public String canales() {
        return "Canales";
    }

    @GetMapping("/metodosPago")
    public String metodosPago() {
        return "metodosPago";
    }

    @GetMapping("/productosCategoria")
    public String mostrarProductoCategoria() {
        return "productosCategoria";
    }

    @GetMapping("/fragment")
    public String cargarFragmento(@RequestParam("name") String name) {
        String path = "fragments/LogginUserFiles/" + name;

        if (name == null || name.contains("..")) {
            return "error/403";
        }

        return path;
    }

    @GetMapping("/fragment/cuenta")
    public String cargarFragmentoCuenta(HttpSession session, Model model) {
        var usuario = session.getAttribute("usuario");
        model.addAttribute("usuario", usuario);
        return "fragments/LogginUserFiles/cuenta :: cuentaFragment";
    }

    @GetMapping("/Detalles")
    public String mostrarDetalles() {
        return "Detalles";
    }
}
