package com.backend.usuarios.auth.oauth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Le dice al frontend qué proveedores están realmente configurados, para que
 * no muestre un botón de Google si nadie puso las credenciales.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ProveedoresController {

    private final ObjectProvider<ClientRegistrationRepository> registros;

    @Value("${app.oauth-redirect-base}")
    private String baseAutorizacion;

    public record ProveedorDisponible(String id, String nombre, String url) {
    }

    @GetMapping("/proveedores")
    public List<ProveedorDisponible> disponibles() {
        ClientRegistrationRepository repositorio = registros.getIfAvailable();
        List<ProveedorDisponible> disponibles = new ArrayList<>();

        if (!(repositorio instanceof InMemoryClientRegistrationRepository enMemoria)) {
            return disponibles;
        }

        for (ClientRegistration registro : enMemoria) {
            disponibles.add(new ProveedorDisponible(
                    registro.getRegistrationId(),
                    registro.getClientName(),
                    baseAutorizacion + "/oauth2/authorization/" + registro.getRegistrationId()));
        }
        return disponibles;
    }
}
