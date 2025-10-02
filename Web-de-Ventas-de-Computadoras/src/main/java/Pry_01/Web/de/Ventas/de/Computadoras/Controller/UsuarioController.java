package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    // ✅ Inyección de dependencias por constructor
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        // ✅ Aquí pasamos la lista de usuarios a la vista
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        return "fragments/Admin-gest/Gest-usuarios :: gest-usuarios";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioRegistro(Model model) {
        model.addAttribute("usuario", new UsuarioModel());
        return "formulario_registro"; 
    }

    @PostMapping
    public String guardarUsuario(UsuarioModel usuarioModel){
        usuarioService.guardarUsuario(usuarioModel);
        return "redirect:/usuarios"; 
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEdicion(@PathVariable Long id, Model model) {
        UsuarioModel usuario = usuarioService.obtenerPorId(id);
        model.addAttribute("usuario", usuario);
        return "formulario_edicion";
    }   

    @GetMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return "redirect:/usuarios"; 
    }
}
