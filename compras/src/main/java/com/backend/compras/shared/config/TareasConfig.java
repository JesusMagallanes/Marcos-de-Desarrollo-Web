package com.backend.compras.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el barrendero de sagas abandonadas y los reintentos de compensación. */
@Configuration
@EnableScheduling
public class TareasConfig {
}
