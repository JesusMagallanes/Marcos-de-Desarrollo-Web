package com.backend.compras.shared.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Timeouts explícitos: sin ellos, una caída de catálogo deja peticiones colgadas hasta
 * agotar el pool de hilos de compras.
 */
@Configuration
public class RestClientConfig {

    @Value("${servicios.catalogo.url}")
    private String catalogoUrl;

    @Value("${servicios.catalogo.timeout-conexion}")
    private Duration timeoutConexion;

    @Value("${servicios.catalogo.timeout-lectura}")
    private Duration timeoutLectura;

    @Bean
    RestClient catalogoRestClient(RestClient.Builder builder) {
        HttpClientSettings ajustes = HttpClientSettings.defaults()
                .withTimeouts(timeoutConexion, timeoutLectura);

        return builder
                .baseUrl(catalogoUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(ajustes))
                .build();
    }

    /** Cliente hacia MercadoPago, con márgenes más amplios por ser externo. */
    @Bean
    RestClient mercadoPagoRestClient(RestClient.Builder builder,
            @Value("${mercadopago.api-url}") String apiUrl) {

        HttpClientSettings ajustes = HttpClientSettings.defaults()
                .withTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(10));

        return builder
                .baseUrl(apiUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(ajustes))
                .build();
    }
}
