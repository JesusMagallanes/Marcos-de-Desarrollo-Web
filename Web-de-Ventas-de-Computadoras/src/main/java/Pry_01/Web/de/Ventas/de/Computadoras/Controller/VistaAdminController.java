package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
@Controller
public class VistaAdminController {

    private final UsuarioService usuarioService;

    public VistaAdminController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/VistaAdmin")
    public String vistaAdmin(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        model.addAttribute("usuario", new UsuarioModel());

        return "VistaAdmin"; // tu plantilla principal
    }
}


