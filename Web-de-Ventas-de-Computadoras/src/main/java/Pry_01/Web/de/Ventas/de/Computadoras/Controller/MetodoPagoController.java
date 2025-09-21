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
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/metodoPago")
public class MetodoPagoController {
    
        private final MetodoPagoService metodoPagoService;

        public MetodoPagoController(MetodoPagoService metodoPagoService) {
            this.metodoPagoService = metodoPagoService;
        }

        @GetMapping
        public List<MetodoPagoModel> listarMetodoPago() {
            return metodoPagoService.listarMetodosPago();
        }

        @PostMapping
        public ResponseEntity<MetodoPagoModel> crearMetodoPago(@Valid @RequestBody MetodoPagoModel metodoPago) {
            MetodoPagoModel nuevo = metodoPagoService.guardarMetodoPago(metodoPago);
            return ResponseEntity.ok(nuevo);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> eliminarEnvios(@PathVariable Long id) {
            metodoPagoService.eliminarMetodoPago(id);
            return ResponseEntity.noContent().build();
        }

        /* @PutMapping("/{id}") */
    
}
