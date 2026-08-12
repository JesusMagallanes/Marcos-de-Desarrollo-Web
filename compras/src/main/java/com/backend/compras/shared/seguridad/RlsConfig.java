package com.backend.compras.shared.seguridad;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enchufa {@link DataSourceRls} delante del pool de la aplicación, para que toda
 * conexión salga con el contexto que consumen las políticas de RLS.
 *
 * Se hace con un BeanPostProcessor y no declarando un `@Bean DataSource` propio
 * a propósito: la autoconfiguración de Spring Boot crea el pool bajo un
 * `@ConditionalOnMissingBean(DataSource.class)`, así que en cuanto se declara un
 * DataSource propio la autoconfiguración se apaga entera y con ella el ajuste de
 * Hikari que hay en application.properties. Envolviendo el bean ya construido se
 * conserva todo eso y solo se le añade una capa.
 *
 * Sobre las migraciones: Flyway usa este mismo DataSource, y con FORCE ROW LEVEL
 * SECURITY las políticas alcanzan también al dueño de las tablas. No es problema
 * para el DDL —crear tablas, índices o políticas no pasa por RLS—, pero una
 * migración futura que haga INSERT o UPDATE sobre `carrito`, `pedido` y demás
 * vería cero filas y se daría por buena sin hacer nada. Esa migración debe
 * empezar por:
 *
 *     SET LOCAL app.omitir_rls = 'on';
 */
@Configuration
public class RlsConfig {

    /**
     * static: un BeanPostProcessor tiene que existir antes que los beans que
     * inspecciona, y sin `static` Spring avisa de que la clase de configuración
     * se instancia demasiado pronto.
     */
    @Bean
    static BeanPostProcessor envolturaRls() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof DataSource fuente && !(bean instanceof DataSourceRls)) {
                    return new DataSourceRls(fuente);
                }
                return bean;
            }
        };
    }
}
