package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Que ningún módulo se quede sin quien lo produzca.
 *
 * <p>Ya pasó una vez. {@code SUELEN_IR_JUNTOS} se declaró para la co-visita en
 * la ficha de producto, la co-visita acabó mezclándose dentro de
 * {@code RELACIONADOS} —que es donde tiene sentido— y aquel módulo se quedó
 * declarado y sin un solo consumidor durante toda una fase.
 *
 * <p>No es solo desorden. Un módulo que nadie produce ensucia la medición con
 * una categoría que siempre vale cero, y quien lea el panel tiene que averiguar
 * si es que no funciona o es que no existe. En un sistema cuyo objetivo es
 * poder medirse, eso importa más de lo que parece.
 *
 * <p>La comprobación es sobre el código fuente, que es tosco y es lo que hay:
 * la alternativa sería un registro de productores que nadie mantendría al día,
 * y eso es cambiar un descuido por otro más caro.
 */
@DisplayName("Módulos de descubrimiento")
class ModuloDescubrimientoTest {

    /** Dónde puede nacer un carrusel. */
    private static final List<Path> PRODUCTORES = List.of(
            Path.of("src/main/java/com/backend/catalogo/descubrimiento"
                    + "/RecomendacionService.java"),
            Path.of("src/main/java/com/backend/catalogo/descubrimiento"
                    + "/DescubrimientoController.java"));

    @Test
    @DisplayName("todos tienen quien los produzca")
    void ningunModuloEstaHuerfano() throws IOException {
        String codigo = leerProductores();

        List<String> huerfanos = Stream.of(ModuloDescubrimiento.values())
                .map(Enum::name)
                .filter(nombre -> !codigo.contains(nombre))
                .toList();

        assertThat(huerfanos)
                .as("declarados pero que no arma nadie: o se usan o se borran")
                .isEmpty();
    }

    @Test
    @DisplayName("cada uno declara su razón por defecto")
    void todosTienenRazon() {
        for (ModuloDescubrimiento modulo : ModuloDescubrimiento.values()) {
            assertThat(modulo.razonPorDefecto())
                    .as("sin razón, lo servido se anota sin poder atribuirse: %s", modulo)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("cada uno declara su origen")
    void todosTienenOrigen() {
        for (ModuloDescubrimiento modulo : ModuloDescubrimiento.values()) {
            assertThat(modulo.origen())
                    .as("el origen es lo que se le dice al usuario: %s", modulo)
                    .isNotNull();
            assertThat(modulo.titulo("algo")).isNotBlank();
        }
    }

    private String leerProductores() throws IOException {
        List<String> partes = new ArrayList<>();
        for (Path ruta : PRODUCTORES) {
            assertThat(ruta)
                    .as("si esta clase se movió, esta prueba deja de comprobar nada")
                    .exists();
            partes.add(Files.readString(ruta, StandardCharsets.UTF_8));
        }
        return String.join("\n", partes);
    }
}
