package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        model.addAttribute("usuario", new UsuarioModel());
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        return "fragments/Admin-gest/Gest-usuarios :: gest-usuarios";
    }

    @PostMapping("/registrar")
    public String registrar(@ModelAttribute UsuarioModel usuario, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.registrarUsuario(usuario);
            redirectAttributes.addFlashAttribute("mensaje", "Usuario registrado correctamente. Puede iniciar sesión.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar usuario");
        }
        return "redirect:/Index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,@RequestParam String password,HttpSession session,Model model) {

        UsuarioModel usuario = usuarioService.login(email, password);

        if (usuario != null) {
            session.setAttribute("usuario", usuario);
            return "redirect:/usuarios/Index-log";
        } else {
            model.addAttribute("error", "Correo o contraseña incorrectos");
            return "/Index";
        }
    }   
}
