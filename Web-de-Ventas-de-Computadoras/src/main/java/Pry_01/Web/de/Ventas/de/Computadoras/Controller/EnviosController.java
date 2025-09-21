package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.EnviosService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/Envios")
public class EnviosController {
    private final EnviosService enviosService;

    public EnviosController(EnviosService enviosService) {
        this.enviosService = enviosService;
    }

    @GetMapping
    public List<EnviosModel> listarEnvios() {
        return enviosService.listarEnvios();
    }

    @PostMapping
    public ResponseEntity<EnviosModel> crearEnvio(@Valid @RequestBody EnviosModel envio) {
        EnviosModel nuevo = enviosService.guardarEnvios(envio);
        return ResponseEntity.ok(nuevo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEnvios(@PathVariable Long id) {
        enviosService.eliminarEnvio(id);
        return ResponseEntity.noContent().build();
    }

    /*@PutMapping("/{id}") */
}
