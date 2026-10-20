package com.backend.web_gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.dedupeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** Enrutado por prefijo hacia los tres servicios de dominio. */
@Configuration
public class RutasConfig {

    /**
     * Catálogo, usuarios y compras tienen su propia configuración CORS además
     * de la del gateway (por si alguien los llama directamente, ya que sus
     * puertos también quedan expuestos en docker-compose). Al proxiar una
     * petición ya resuelta por el CORS del gateway, la respuesta del servicio
     * de abajo trae ADEMÁS su propia cabecera Access-Control-Allow-Origin, y
     * las dos terminan en la respuesta final. El navegador la rechaza por
     * tener el origen duplicado en la misma cabecera. `dedupeResponseHeader`
     * se queda solo con el primer valor (el que puso el gateway).
     */
    private static final String CABECERAS_CORS_A_DEDUPLICAR =
            "Access-Control-Allow-Origin Access-Control-Allow-Credentials";

    @Value("${servicios.catalogo.url}")
    private String catalogoUrl;

    @Value("${servicios.usuarios.url}")
    private String usuariosUrl;

    @Value("${servicios.compras.url}")
    private String comprasUrl;

    @Bean
    RouterFunction<ServerResponse> rutasCatalogo() {
        return route("catalogo")
                .route(path("/api/productos/**"), http())
                .route(path("/api/categorias/**"), http())
                .route(path("/api/marcas/**"), http())
                .route(path("/api/guias/**"), http())
                .route(path("/api/chatbot/**"), http())
                .route(path("/api/valoraciones/**"), http())
                /*
                 * La cola de sincronización sin conexión.
                 *
                 * Estaba SIN enrutar: el navegador guarda las valoraciones
                 * escritas sin red y al recuperarla las manda a
                 * `/api/sync/valoraciones`, que sin esta línea no llegaba a
                 * catálogo. No daba error de red —el gateway devolvía el
                 * index.html de la SPA para cualquier ruta desconocida—, así
                 * que la cola creía haber fallado y lo guardado no se
                 * publicaba nunca. El proxy de desarrollo apunta también aquí,
                 * de modo que tampoco funcionaba con `ng serve`.
                 */
                .route(path("/api/sync/**"), http())
                // Descubrimiento: eventos, recomendaciones y tendencias.
                .route(path("/api/descubrimiento/**"), http())
                // IA aplicada: búsqueda en lenguaje natural, fichas, moderación, reseñas.
                .route(path("/api/ia/**"), http())
                .before(uri(catalogoUrl))
                .after(dedupeResponseHeader(CABECERAS_CORS_A_DEDUPLICAR))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> rutasUsuarios() {
        return route("usuarios")
                .route(path("/api/auth/**"), http())
                .route(path("/api/usuarios/**"), http())
                .route(path("/api/roles/**"), http())
                // Catalogo de departamentos, provincias y distritos del Peru.
                .route(path("/api/ubigeo/**"), http())
                // Solicitudes para vender en la tienda: viven en `usuarios`
                // porque lo que conceden es un rol, no un producto.
                .route(path("/api/colaboradores/**"), http())
                .before(uri(usuariosUrl))
                .after(dedupeResponseHeader(CABECERAS_CORS_A_DEDUPLICAR))
                .build();
    }

    /** Handshake con Google y Facebook. */
    @Bean
    RouterFunction<ServerResponse> rutasOAuth() {
        return route("oauth")
                .route(path("/oauth2/**"), http())
                .route(path("/login/oauth2/**"), http())
                .before(uri(usuariosUrl))
                .after(dedupeResponseHeader(CABECERAS_CORS_A_DEDUPLICAR))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> rutasCompras() {
        return route("compras")
                .route(path("/api/carrito/**"), http())
                .route(path("/api/pedidos/**"), http())
                .route(path("/api/metodos-pago/**"), http())
                .route(path("/api/envios/**"), http())
                .route(path("/api/pagos/**"), http())
                .before(uri(comprasUrl))
                .after(dedupeResponseHeader(CABECERAS_CORS_A_DEDUPLICAR))
                .build();
    }

    /**
     * /api/inventario/** NO se expone aquí: es contrato interno entre compras y catálogo,
     * y no debe ser alcanzable desde el navegador.
     */
}