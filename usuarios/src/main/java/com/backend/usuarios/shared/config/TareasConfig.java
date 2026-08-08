package com.backend.usuarios.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas periódicas (purga del limitador, tokens revocados). */
@Configuration
@EnableScheduling
public class TareasConfig {
}
