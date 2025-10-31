package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class VistaAdminController {

    private final UsuarioService usuarioService;
    private final ProductoService productoService;
    private final CategoriaService categoriaService;
    private final MetodoPagoService metodoPagoService;

    public VistaAdminController(UsuarioService usuarioService, CategoriaService categoriaService,
            ProductoService productoService, MetodoPagoService metodoPagoService) {
        this.usuarioService = usuarioService;
        this.categoriaService = categoriaService;
        this.productoService = productoService;
        this.metodoPagoService = metodoPagoService;
        
    }

    @GetMapping("/VistaAdmin")
    public String vistaAdmin(@RequestParam(required = false) String seccion, Model model) {
        log.info("Cargando VistaAdmin con sección: " + seccion);
        model.addAttribute("seccion", seccion);

        try {
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());
            
        } catch (Exception e) {
            log.error("Error al preparar VistaAdmin", e);
            throw e;
        }

        return "admin/VistaAdmin";
    }
}
