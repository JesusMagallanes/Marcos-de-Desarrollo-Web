package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/Index")
    public String indexLog(HttpSession session, Model model) {
        try {
            Object obj = session.getAttribute("usuario");
            if (obj == null || !(obj instanceof UsuarioDTO)) {
                return "redirect:/Index";
            }
            UsuarioDTO usuario = (UsuarioDTO) obj;
            model.addAttribute("usuario", usuario);
            return "/Index";
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

    @PostMapping("/perfil/editar/{id}")
    public String editarPerfil(@PathVariable Long id,
            @ModelAttribute UsuarioUpdateDTO datos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UsuarioDTO usuarioEnSesion = (UsuarioDTO) session.getAttribute("usuario");
        if (usuarioEnSesion == null || !usuarioEnSesion.getId().equals(id)) {
            return "redirect:/login";
        }
        UsuarioModel usuarioExistente = usuarioService.obtenerPorId(id);
        if (usuarioExistente == null) {
            return "redirect:/Loggin-User?error=notfound";
        }
        usuarioExistente.setName(datos.getName());
        usuarioExistente.setLastname(datos.getLastname());
        usuarioExistente.setEmailAddress(datos.getEmailAddress());
        usuarioExistente.setPhoneNumber(datos.getPhoneNumber());
        usuarioExistente.setAddress(datos.getAddress());

        usuarioService.guardarUsuario(usuarioExistente); 

        redirectAttributes.addFlashAttribute("successMessage", "¡Cambios guardados exitosamente!");
        
        session.setAttribute("usuario", new UsuarioDTO(usuarioExistente));
        return "redirect:/usuarios/Loggin-User";
    }

    @GetMapping("/Loggin-User")
    public String mostrarLogginUser(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/Index";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "Loggin-User";
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
            UsuarioModel usuarioRegistrado = usuarioService.registrarUsuario(usuario);
            UsuarioDTO usuarioDTO = new UsuarioDTO(usuarioRegistrado);
            session.setAttribute("usuario", usuarioDTO);
            redirectAttributes.addFlashAttribute("mensaje", "Usuario registrado y sesión iniciada.");
            return "redirect:/usuarios/Index";
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
                return "redirect:/usuarios/Index";
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

    @GetMapping("/Canales")
    public String mostrarCanales(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/Canales";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "Canales";
    }

    @GetMapping("/Carrito")
    public String mostrarCarrito(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/Carrito";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "Carrito";
    }

    @GetMapping("/Detalles")
    public String mostrarDetalles(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/Detalles";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "Detalles";
    }

    @GetMapping("/EnviosPag")
    public String mostrarEnviosPag(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/EnviosPag";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "EnviosPag";
    }

    @GetMapping("/metodosPago")
    public String mostrarMetodosPago(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/metodosPago";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "metodosPago";
    }

    @GetMapping("/productosCategoria")
    public String mostrarProductosCategoria(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/productosCategoria";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "productosCategoria";
    }

    @GetMapping("/Somos")
    public String mostrarSomos(HttpSession session, Model model) {
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        if (usuarioDTO == null) {
            return "redirect:/Somos";
        }

        model.addAttribute("usuario", usuarioDTO);
        return "Somos";
    }
}