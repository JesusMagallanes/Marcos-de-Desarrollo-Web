package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;


@Controller
public class HomeController {
    private static final String[] USER = {"Canales","Detalles","Somos","Canales"};

    @GetMapping("/")
   public String Principal() {
       return "/Index";
   }
   
   @GetMapping("/{view}")
   public String page(@PathVariable String view) {
       for (String u : USER) {
           if (u.equals(view)) return view;
       }
       throw new ResponseStatusException(HttpStatus.NOT_FOUND);
   }

}