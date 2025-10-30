package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.validation.Valid;
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
    
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/admin/crear-admin")
    public String crearAdmin(@ModelAttribute("usuario") @Valid UsuarioModel usuario,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model) {
        log.info("Solicitud de creación de admin: {}", usuario.getEmailAddress());

        // validación del formulario
        if (bindingResult.hasErrors()) {
            // devolver la misma vista con errores y datos ya cargados
            model.addAttribute("seccion", "crearAdmin");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());
            return "admin/VistaAdmin";
        }

        try {
            usuarioService.registrarAdmin(usuario);
            redirectAttributes.addFlashAttribute("successMessage", "Admin creado correctamente: " + usuario.getEmailAddress());
        } catch (IllegalArgumentException e) {
            log.warn("No se pudo crear admin: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error creando admin", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error interno al crear admin");
        }

        return "redirect:/VistaAdmin?seccion=usuarios";
    }
}
