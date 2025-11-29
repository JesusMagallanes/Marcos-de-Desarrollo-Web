package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MpPreferenceRequest;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MercadoPagoService;

import java.util.Map;

@RestController
@RequestMapping("/api/mercadopago")
public class MercadoPagoController {

    @Autowired
    private MercadoPagoService mpService;

    @PostMapping("/create_preference")
    public ResponseEntity<?> createPreference(@RequestBody MpPreferenceRequest req) {
        try {
            Map<String, Object> pref = mpService.createPreference(req);
            // devolver tal cual la respuesta de MercadoPago (contendrá id, init_point, sandbox_init_point)
            return ResponseEntity.ok(pref);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
