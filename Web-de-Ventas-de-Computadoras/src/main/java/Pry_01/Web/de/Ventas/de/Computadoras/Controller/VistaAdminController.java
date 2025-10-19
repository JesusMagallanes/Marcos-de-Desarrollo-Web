package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/admin")
public class VistaAdminController {

    private final PasswordEncoder passwordEncoder;
    private final UsuarioService usuarioService;

    public VistaAdminController(UsuarioService usuarioService, PasswordEncoder passwordEncoder) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
    }

    // Carga la vista principal del panel admin
    @GetMapping
    public String vistaAdmin(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        model.addAttribute("usuario", new UsuarioModel());
        return "admin/VistaAdmin";
    }

    // Registro de usuario desde el offcanvas
    @PostMapping("/usuarios/registrar")
    public String guardarUsuario(@Valid @ModelAttribute("usuario") UsuarioModel usuarioModel) {
        usuarioModel.setPassword(passwordEncoder.encode(usuarioModel.getPassword()));
        usuarioService.guardarUsuario(usuarioModel);
        return "redirect:/admin";
    }

    // Edición de usuario
    @PostMapping("/usuarios/editar/{id}")
    public String actualizarUsuario(@PathVariable Long id, @Valid @ModelAttribute("usuario") UsuarioModel usuario) {
        usuario.setId(id);
        usuarioService.actualizarUsuario(id, usuario);
        return "redirect:/admin";
    }

    // Eliminación de usuario
    @PostMapping("/usuarios/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return "redirect:/admin";
    }
}