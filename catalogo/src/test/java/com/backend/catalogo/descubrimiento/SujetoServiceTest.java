package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * Quién es el sujeto de una petición, y qué pasa cuando el anónimo inicia
 * sesión.
 *
 * <p>Aquí vive la regla de seguridad del módulo: <b>si hay JWT, la cabecera
 * {@code X-Sujeto} se ignora</b>. Sin ella, cualquiera podría leer o
 * contaminar el perfil de otro poniendo su identificador en una cabecera, y el
 * endpoint es público a propósito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Identidad del sujeto")
class SujetoServiceTest {

    private static final Long ANA = 42L;

    @Mock
    private SujetoRepository repositorio;

    private SujetoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new SujetoService(repositorio, new PesosDescubrimiento());
        when(repositorio.save(any(Sujeto.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Sujeto sujeto(UUID id, Long usuarioId) {
        return Sujeto.builder().id(id).usuarioId(usuarioId)
                .creadoEn(Instant.now()).vistoEn(Instant.now()).build();
    }

    @Test
    @DisplayName("con sesión iniciada, la cabecera del cliente NO puede suplantar")
    void elJwtGanaSobreLaCabecera() {
        /*
         * El ataque que esto impide: alguien conoce el identificador de otro
         * —lo vio en un log, en un enlace compartido— y lo manda en la
         * cabecera esperando leer su perfil. Con sesión iniciada, el sujeto
         * sale de la cuenta y punto.
         */
        UUID deOtro = UUID.randomUUID();
        Sujeto cuenta = sujeto(UUID.randomUUID(), ANA);
        when(repositorio.buscarVivoDeUsuario(ANA)).thenReturn(Optional.of(cuenta));
        // El id ajeno pertenece a OTRA cuenta, así que no es fusionable.
        when(repositorio.findById(deOtro)).thenReturn(Optional.of(sujeto(deOtro, 99L)));

        Sujeto resuelto = servicio.resolver(ANA, deOtro, null);

        assertThat(resuelto.getId()).isEqualTo(cuenta.getId());
        assertThat(resuelto.getId()).isNotEqualTo(deOtro);
        // Y no se toca nada del sujeto ajeno.
        verify(repositorio, never()).moverEventos(eq(deOtro), any());
        verify(repositorio, never()).fundirPerfil(eq(deOtro), any(), anyLong());
    }

    @Test
    @DisplayName("al iniciar sesión, el rastro anónimo se funde con la cuenta")
    void fusionAnonimoAutenticado() {
        /*
         * El caso que da sentido al concepto de sujeto: navega el lunes sin
         * cuenta, mira veinte productos, aplica filtros; el martes inicia
         * sesión. Sin fusión, todo ese aprendizaje se tira.
         */
        UUID anonimo = UUID.randomUUID();
        Sujeto cuenta = sujeto(UUID.randomUUID(), ANA);
        when(repositorio.buscarVivoDeUsuario(ANA)).thenReturn(Optional.of(cuenta));
        when(repositorio.findById(anonimo)).thenReturn(Optional.of(sujeto(anonimo, null)));

        Sujeto resuelto = servicio.resolver(ANA, anonimo, "110101");

        assertThat(resuelto.getId()).isEqualTo(cuenta.getId());
        verify(repositorio).moverEventos(anonimo, cuenta.getId());
        verify(repositorio).moverImpresiones(anonimo, cuenta.getId());
        verify(repositorio).fundirPerfil(eq(anonimo), eq(cuenta.getId()), anyLong());
        verify(repositorio).fundirDescartes(anonimo, cuenta.getId());
    }

    @Test
    @DisplayName("el sujeto anónimo fusionado queda como redirección, no se borra")
    void elAnonimoQuedaComoRedireccion() {
        /*
         * Puede haber pestañas abiertas mandando todavía el id viejo. Borrar
         * la fila tiraría esos eventos; dejarla apuntando al superviviente los
         * salva.
         */
        UUID anonimo = UUID.randomUUID();
        Sujeto viejo = sujeto(anonimo, null);
        Sujeto cuenta = sujeto(UUID.randomUUID(), ANA);
        when(repositorio.buscarVivoDeUsuario(ANA)).thenReturn(Optional.of(cuenta));
        when(repositorio.findById(anonimo)).thenReturn(Optional.of(viejo));

        servicio.resolver(ANA, anonimo, null);

        assertThat(viejo.getFusionadoEn()).isEqualTo(cuenta.getId());
        assertThat(viejo.estaFusionado()).isTrue();
    }

    @Test
    @DisplayName("volver con un id ya fusionado lleva al sujeto superviviente")
    void siguelaCadenaDeFusiones() {
        UUID anonimo = UUID.randomUUID();
        Sujeto cuenta = sujeto(UUID.randomUUID(), ANA);
        Sujeto fusionado = sujeto(anonimo, null);
        fusionado.setFusionadoEn(cuenta.getId());

        when(repositorio.findById(anonimo)).thenReturn(Optional.of(fusionado));
        when(repositorio.findById(cuenta.getId())).thenReturn(Optional.of(cuenta));

        // Sin sesión iniciada y con el id viejo: aun así llega a su perfil.
        Sujeto resuelto = servicio.resolver(null, anonimo, null);

        assertThat(resuelto.getId()).isEqualTo(cuenta.getId());
    }

    @Test
    @DisplayName("un visitante sin identificador estrena sujeto")
    void visitanteNuevo() {
        Sujeto resuelto = servicio.resolver(null, null, "110101");

        assertThat(resuelto.getId()).isNotNull();
        assertThat(resuelto.getUsuarioId()).isNull();
        assertThat(resuelto.getUbigeo()).isEqualTo("110101");
    }

    @Test
    @DisplayName("un identificador que no está en la base se acepta como semilla")
    void identificadorDesconocido() {
        /*
         * Viene de una cookie legítima que no llegó a persistirse. Rechazarlo
         * obligaría al cliente a cambiar de identificador y a perder su propio
         * rastro, que es peor que aceptarlo: un UUID v4 no se adivina.
         */
        UUID deLaCookie = UUID.randomUUID();
        when(repositorio.findById(deLaCookie)).thenReturn(Optional.empty());

        Sujeto resuelto = servicio.resolver(null, deLaCookie, null);

        assertThat(resuelto.getId()).isEqualTo(deLaCookie);
    }
}
