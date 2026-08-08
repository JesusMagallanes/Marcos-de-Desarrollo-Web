package com.backend.web_gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita la purga periódica de ventanas del limitador de peticiones. */
@Configuration
@EnableScheduling
public class TareasConfig {
}
