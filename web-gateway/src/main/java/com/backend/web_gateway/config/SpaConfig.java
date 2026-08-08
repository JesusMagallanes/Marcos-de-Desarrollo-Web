package com.backend.web_gateway.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Sirve el bundle de Angular desde classpath:/static. */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {

                    @Override
                    protected Resource getResource(String rutaSolicitada, Resource ubicacion) throws IOException {
                        Resource recurso = ubicacion.createRelative(rutaSolicitada);

                        if (recurso.exists() && recurso.isReadable()) {
                            return recurso;
                        }
                        // Las llamadas a la API nunca caen aquí: las atrapa el gateway antes.
                        if (rutaSolicitada.startsWith("api/")) {
                            return null;
                        }
                        return new org.springframework.core.io.ClassPathResource("/static/index.html");
                    }
                });
    }
}
