package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.MercadoPagoConfig;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MpPreferenceRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Service
public class MercadoPagoService {

    @Autowired
    private MercadoPagoConfig mpConfig;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> createPreference(MpPreferenceRequest req) throws Exception {
        // Construir payload minimalista para la API de MercadoPago
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> item = new HashMap<>();
        item.put("title", req.getTitle() != null ? req.getTitle() : "Producto");
        item.put("quantity", req.getQuantity() != null ? req.getQuantity() : 1);
        item.put("unit_price", req.getUnitPrice() != null ? req.getUnitPrice() : 0.0);
        item.put("id", req.getId());
        payload.put("items", new Object[]{ item });

        String body = mapper.writeValueAsString(payload);

        String token = mpConfig.getAccessToken();
        if (token == null || token.isEmpty() || token.startsWith("REEMPLAZA")) {
            throw new IllegalStateException("MP access token no configurado. Define MP_ACCESS_TOKEN.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/checkout/preferences"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // parsear JSON y devolver mapa
            Map<String, Object> resp = mapper.readValue(response.body(), Map.class);
            return resp;
        } else {
            throw new RuntimeException("MP API error: " + response.statusCode() + " - " + response.body());
        }
    }
}
