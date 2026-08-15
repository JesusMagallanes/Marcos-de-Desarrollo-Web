package com.backend.usuarios.shared.config;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.backend.usuarios.usuario.Permiso;
import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.RolRepository;
import com.backend.usuarios.usuario.TipoRol;

/**
 * Siembra los roles de sistema si faltan. Son idempotente: si el rol ya
 * existe, se respeta su configuración (permisos editados desde el panel).
 *
 * El seed corre ANTES que AdminSeeder para que el rol ADMINISTRADOR exista
 * cuando se crea la cuenta inicial.
 */
@Configuration
public class RolSeeder {

    @Bean
    @Order(0)
    CommandLineRunner sembrarRoles(RolRepository repositorio) {
        return args -> {
            crearSiFalta(repositorio, "CLIENTE", "Cliente de la tienda: compra y valora.",
                    TipoRol.CLIENTE, Set.of());

            crearSiFalta(repositorio, "EMPLEADO",
                    "Personal de la tienda: atiende pedidos, envíos y valoraciones.",
                    TipoRol.TRABAJADOR,
                    Set.of(Permiso.PEDIDOS_GESTIONAR, Permiso.ENVIOS_GESTIONAR,
                            Permiso.VALORACIONES_GESTIONAR));

            crearSiFalta(repositorio, "ADMINISTRADOR",
                    "Administración completa de la tienda.",
                    TipoRol.TRABAJADOR, EnumSet.allOf(Permiso.class));

            // Vendedor externo aprobado por un administrador (ver
            // docs/contrato-colaboradores.md).
            //
            // `TipoRol.CLIENTE` y no TRABAJADOR: vende en la tienda, pero no es
            // personal de la casa ni entra al panel de administración.
            //
            // PRODUCTOS_PROPIOS y no PRODUCTOS_GESTIONAR: aquel da el catálogo
            // entero, incluidos los productos ajenos.
            //
            // OJO: `catalogo` todavía no comprueba este permiso ni sabe de quién
            // es cada producto (ticket SZ-B08). Hasta que lo haga, tenerlo no
            // concede nada; está aquí para que el frente 2 tenga contra qué
            // programar y no se invente otro nombre.
            crearSiFalta(repositorio, "COLABORADOR",
                    "Vendedor externo: publica productos que pasan por moderación.",
                    TipoRol.CLIENTE, Set.of(Permiso.PRODUCTOS_PROPIOS));
        };
    }

    private void crearSiFalta(RolRepository repositorio, String nombre, String descripcion,
            TipoRol tipo, Set<Permiso> permisos) {
        if (repositorio.existsById(nombre)) {
            return;
        }
        repositorio.save(Rol.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .tipo(tipo)
                .sistema(true)
                .permisos(permisos)
                .build());
    }
}
