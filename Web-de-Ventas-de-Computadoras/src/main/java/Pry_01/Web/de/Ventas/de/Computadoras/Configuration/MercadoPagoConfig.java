package Pry_01.Web.de.Ventas.de.Computadoras.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoConfig {

    @Value("${mercadopago.access.token:}")
    private String accessToken;

    @Value("${mercadopago.public.key:}")
    private String publicKey;

    public String getAccessToken() {
        return accessToken;
    }

    public String getPublicKey() {
        return publicKey;
    }
}
