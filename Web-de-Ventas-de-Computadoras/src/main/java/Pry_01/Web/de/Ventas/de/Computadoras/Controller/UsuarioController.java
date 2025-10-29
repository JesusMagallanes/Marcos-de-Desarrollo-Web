package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
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

    @GetMapping("/Index-log")
    public String indexLog(HttpSession session, Model model) {
        try {
            Object obj = session.getAttribute("usuario");
            if (obj == null || !(obj instanceof UsuarioDTO)) {
                return "redirect:/Index";
            }
            UsuarioDTO usuario = (UsuarioDTO) obj;
            model.addAttribute("usuario", usuario);
            return "user/Index-log";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("message", "Error interno: " + e.getMessage());
            return "error/500";
        }
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
    public String registrar(@ModelAttribute UsuarioModel usuario,
                            RedirectAttributes redirectAttributes,
                            HttpSession session) {
        try {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
            UsuarioModel usuarioRegistrado = usuarioService.registrarUsuario(usuario);

            UsuarioDTO usuarioDTO = new UsuarioDTO(usuarioRegistrado);
            session.setAttribute("usuario", usuarioDTO);

            redirectAttributes.addFlashAttribute("mensaje", "Usuario registrado y sesión iniciada.");
            return "redirect:/usuarios/Index-log";
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error al registrar: " + e.getMessage());
            return "redirect:/Index";
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam(value = "email") String email,
                        @RequestParam(value = "password") String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        try {
            if (email == null || password == null || email.isBlank() || password.isBlank()) {
                redirectAttributes.addFlashAttribute("errorl", "Faltan credenciales");
                return "redirect:/Index";
            }

            UsuarioModel usuario = usuarioService.login(email, password);
            if (usuario != null) {
                UsuarioDTO usuarioDTO = new UsuarioDTO(usuario);
                session.setAttribute("usuario", usuarioDTO);
                return "redirect:/usuarios/Index-log";
            } else {
                redirectAttributes.addFlashAttribute("errorl", "Correo o contraseña incorrectos");
                return "redirect:/Index";
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorl", "Error interno al iniciar sesión");
            return "redirect:/Index";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("mensaje", "Sesión cerrada correctamente.");
        return "redirect:/Index";
    }
}