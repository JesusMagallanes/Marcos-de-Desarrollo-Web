package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object statusCode = request.getAttribute("jakarta.servlet.error.status_code");
        int code = (statusCode != null) ? Integer.parseInt(statusCode.toString()) : 0;

        String errorMsg;

        switch (code) {
            case 400:
                errorMsg = "Solicitud incorrecta";
                break;
            case 401:
                errorMsg = "No autorizado";
                break;
            case 403:
                errorMsg = "Acceso prohibido";
                break;
            case 404:
                errorMsg = "Página no encontrada";
                break;
            case 500:
                errorMsg = "Error interno del servidor";
                break;
            default:
                errorMsg = "Ha ocurrido un error inesperado (" + code + ")";
                break;
        }

        model.addAttribute("code", code);
        model.addAttribute("message", errorMsg);

        
        return "error/error";
    }
}
