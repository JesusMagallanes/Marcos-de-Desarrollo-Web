package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
        System.out.println("Rol del usuario: " + usuario.getRol());
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return "Redirect:/VistaAdmin";
    }
}
