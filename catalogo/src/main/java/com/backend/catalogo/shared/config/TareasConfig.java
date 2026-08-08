package com.backend.catalogo.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el barrido de reservas caducadas y la purga del limitador. */
@Configuration
@EnableScheduling
public class TareasConfig {
}
