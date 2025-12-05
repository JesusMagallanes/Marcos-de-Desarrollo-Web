package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MarcaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class IndexController {
    private final CategoriaService categoriaService;
    private final MarcaService marcaService;
    private final ProductoService productoService;

    @ModelAttribute
    public void addUsuarioToModel(HttpSession session, Model model) {
        var usuario = session.getAttribute("usuario");
        if (usuario != null) {
            model.addAttribute("usuario", usuario);
        }
    }

    @GetMapping({ "/", "/Index" })
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        List<MarcaResponseDTO> marcas = marcaService.listarMarca();
        List<ProductosResponseDTO> productos = productoService.listarProducto();

        model.addAttribute("categorias", categorias);
        model.addAttribute("marcas", marcas);
        model.addAttribute("productos", productos);

        Map<Long, List<ProductosResponseDTO>> productosPorCategoria = new LinkedHashMap<>();
        for (CategoriaModel cat : categorias) {
            List<ProductosResponseDTO> filt = productos.stream()
                    .filter(p -> p.getCategoriaName() != null && p.getCategoriaName().equals(cat.getName()))
                    .toList();
            productosPorCategoria.put(cat.getId(), filt);
        }
        model.addAttribute("productosPorCategoria", productosPorCategoria);

        Map<Long, List<List<ProductosResponseDTO>>> productosPorCategoriaChunks = new LinkedHashMap<>();
        int chunkSize = 6;
        for (Map.Entry<Long, List<ProductosResponseDTO>> entry : productosPorCategoria.entrySet()) {
            List<ProductosResponseDTO> list = entry.getValue();
            List<List<ProductosResponseDTO>> chunks = new ArrayList<>();
            if (list != null && !list.isEmpty()) {
                for (int i = 0; i < list.size(); i += chunkSize) {
                    int end = Math.min(list.size(), i + chunkSize);
                    chunks.add(new ArrayList<>(list.subList(i, end)));
                }
            }
            productosPorCategoriaChunks.put(entry.getKey(), chunks);
        }
        model.addAttribute("productosPorCategoriaChunks", productosPorCategoriaChunks);

        int topChunkSize = 5;
        List<List<ProductosResponseDTO>> productosTopChunks = new ArrayList<>();
        if (productos != null && !productos.isEmpty()) {
            for (int i = 0; i < productos.size(); i += topChunkSize) {
                int end = Math.min(productos.size(), i + topChunkSize);
                productosTopChunks.add(new ArrayList<>(productos.subList(i, end)));
            }
        }
        model.addAttribute("productosTopChunks", productosTopChunks);

        return "Index";
    }

    @GetMapping("/EnviosPag")
    public String enviosPag() {
        return "EnviosPag";
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
