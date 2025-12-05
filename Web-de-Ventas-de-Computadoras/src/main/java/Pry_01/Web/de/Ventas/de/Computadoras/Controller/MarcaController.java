package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MarcaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/marcas")
@RequiredArgsConstructor
public class MarcaController {

    private final MarcaService marcaService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;

    @GetMapping("/api")
    @ResponseBody
    public List<MarcaModel> listarMarcasApi() {
        return marcaService.listarMarcas();
    }

    @GetMapping("/categoria/{categoriaId}")
    @ResponseBody
    public List<MarcaModel> listarPorCategoria(@PathVariable Long categoriaId) {
        return marcaService.listarMarcasPorCategoria(categoriaId);
    }

    @PostMapping
    public String guardarMarca(@Valid @ModelAttribute("marca") MarcaCreateDTO dto,
                               BindingResult result,
                               Model model) {

        if (result.hasErrors()) {
            model.addAttribute("seccion", "marca");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", dto);
            model.addAttribute("marcas", marcaService.listarMarcas());
            return "admin/VistaAdmin";
        }

        marcaService.guardarMarca(dto);
        return "redirect:/VistaAdmin?seccion=marca";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarMarca(@PathVariable Long id) {
        marcaService.eliminarMarca(id);
        return "redirect:/VistaAdmin?seccion=marca";
    }

    @PostMapping("/editar/{id}")
    public String editarMarca(@PathVariable Long id,
                              @Valid @ModelAttribute("marca") MarcaUpdateDTO dto,
                              BindingResult result,
                              Model model) {

        if (result.hasErrors()) {
            model.addAttribute("seccion", "marca");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("marca", dto);

            return "admin/VistaAdmin";
        }

        marcaService.actualizarMarca(id, dto);
        return "redirect:/VistaAdmin?seccion=marca";
    }
}
