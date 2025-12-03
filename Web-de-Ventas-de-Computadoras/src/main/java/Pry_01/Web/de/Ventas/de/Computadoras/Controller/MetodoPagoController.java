package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;

@Slf4j
@Controller
@RequestMapping("/metodoPagos")
public class MetodoPagoController {

    private final MetodoPagoService metodoPagoService;

    public MetodoPagoController(MetodoPagoService metodoPagoService) {
        this.metodoPagoService = metodoPagoService;
    }

    @GetMapping("/api")
    @ResponseBody
    public List<MetodoPagoResponseDTO> listarMetodoPago() {
        return metodoPagoService.listarMetodosPago();
    }

    @PostMapping
    public String guardarMetodoPago(@Valid @ModelAttribute("metodoPago") MetodoPagoCreateDTO dto,
            BindingResult result,
            Model model) {
        log.info("Intentando guardar metodo de pago: {}", dto);

        if (result.hasErrors()) {
            log.warn("Errores de validación al guardar metodo de pago: {}", result.getAllErrors());
            model.addAttribute("metodoPago", dto);
            model.addAttribute("metodosPago", metodoPagoService.listarMetodosPago());
            return "admin/VistaAdmin?seccion=metodoPago";
        }

        metodoPagoService.guardarMetodoPago(dto);
        log.info("Metodo de pago guardado exitosamente: {}", dto);
        return "redirect:/VistaAdmin?seccion=metodoPago";
    }

    @PostMapping("/editar/{id}")
    public String editarMetodoPago(@PathVariable Long id, @Valid @ModelAttribute("metodoPago") MetodoPagoUpdateDTO dto,
            BindingResult result,
            Model model) {
        log.info("Intentando editar metodo de pago con ID {}: {}", id, dto);

        if (result.hasErrors()) {
            log.warn("Errores de validación al editar metodo de pago con ID {}: {}", id, result.getAllErrors());
            model.addAttribute("metodoPago", dto);
            model.addAttribute("metodosPago", metodoPagoService.listarMetodosPago());
            return "admin/VistaAdmin?seccion=metodoPago";
        }

        metodoPagoService.actualizarMetodoPago(id, dto);
        log.info("Metodo de pago con ID {} editado exitosamente: {}", id, dto);
        return "redirect:/VistaAdmin?seccion=metodoPago";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarMetodoPago(@PathVariable Long id) {
        metodoPagoService.eliminarMetodoPago(id);
        return "redirect:/VistaAdmin?seccion=metodoPago";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public MetodoPagoResponseDTO obtenerMetodoPago(@PathVariable Long id) {
        return metodoPagoService.obtenerPorId(id);
    }
}