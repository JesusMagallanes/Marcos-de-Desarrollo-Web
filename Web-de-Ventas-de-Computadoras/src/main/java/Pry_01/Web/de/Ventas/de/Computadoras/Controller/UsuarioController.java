package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioService usuarioService, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        model.addAttribute("usuario", new UsuarioModel());
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        return "fragments/Admin-gest/Gest-usuarios :: gest-usuarios";
    }

    @PostMapping
    public String guardarUsuario(@ModelAttribute("usuario") UsuarioModel usuarioModel) {
        usuarioModel.setPassword(passwordEncoder.encode(usuarioModel.getPassword()));
        usuarioService.guardarUsuario(usuarioModel);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/editar/{id}")
    public String editarUsuario(@PathVariable Long id,
            @ModelAttribute("usuario") UsuarioModel usuario) {
        usuario.setId(id);
        usuarioService.actualizarUsuario(usuario);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/registrar")
    public String registrar(@ModelAttribute UsuarioModel usuario, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.registrarUsuario(usuario);
            redirectAttributes.addFlashAttribute("mensaje", "Usuario registrado correctamente. Puede iniciar sesión.");
        } catch (Exception e) {
        e.printStackTrace();

        redirectAttributes.addFlashAttribute("error", "Error al registrar usuario");

        return "error/500";
        }
        return "redirect:/Index";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            Model model) {

        UsuarioModel usuario = usuarioService.login(email, password);

        if (usuario != null) {
            session.setAttribute("usuario", usuario);

            return "redirect:/usuarios/Index-log";
        } else {
            model.addAttribute("errorl", "Correo o contraseña incorrectos");
            return "/Index";
        }
    }
}
