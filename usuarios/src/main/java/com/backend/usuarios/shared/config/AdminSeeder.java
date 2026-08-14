package com.backend.usuarios.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;

/** Crea el administrador inicial si no existe. */
@Configuration
@Slf4j
public class AdminSeeder {

    @Bean
    CommandLineRunner sembrarAdministrador(UsuarioRepository repositorio,
            PasswordEncoder codificador,
            @Value("${admin.seed.email}") String email,
            @Value("${admin.seed.password}") String password) {

        return args -> {
            if (password == null || password.isBlank()) {
                log.info("ADMIN_PASSWORD no definido: se omite la creación del administrador inicial.");
                return;
            }

            String correo = email.trim().toLowerCase();
            if (repositorio.existsByEmailAddress(correo)) {
                log.info("El administrador {} ya existe.", correo);
                return;
            }

            repositorio.save(Usuario.builder()
                    .name("Admin")
                    .lastname("Principal")
                    .emailAddress(correo)
                    .password(codificador.encode(password))
                    .phoneNumber("999999999")
                    .address("Oficina central")
                    .rol("ADMINISTRADOR")
                    .build());

            log.info("Administrador inicial creado: {}", correo);
        };
    }
}
