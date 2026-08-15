package com.backend.usuarios.colaborador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backend.usuarios.shared.error.ConflictoException;

/**
 * Las transiciones de la solicitud.
 *
 * <p>Aprobar concede permiso para publicar en el catálogo, así que las reglas de
 * qué se puede resolver y cuándo no pueden depender de que el servicio se
 * acuerde de comprobarlas: van en la entidad y se prueban aquí.
 */
class SolicitudColaboradorTest {

    private SolicitudColaborador pendiente() {
        return SolicitudColaborador.builder()
                .id(1L)
                .usuarioId(42L)
                .nombreComercial("Importaciones Vega")
                .documento("20512345678")
                .telefonoContacto("987654321")
                .direccion("Av. Los Próceres 1420")
                .rubro("Componentes de PC")
                .descripcion("Importamos teclados mecánicos desde 2019.")
                .estado(EstadoSolicitud.PENDIENTE)
                .creadaEn(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("aprobar")
    class Aprobar {

        @Test
        @DisplayName("deja constancia de quién y cuándo, no solo del estado")
        void dejaTrazaCompleta() {
            SolicitudColaborador s = pendiente();
            s.aprobar(7L);

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
            assertThat(s.getResueltaPor()).isEqualTo(7L);
            assertThat(s.getResueltaEn()).isNotNull();
            // Una aprobada no puede arrastrar un motivo de rechazo: la base lo
            // impide con un CHECK y la entidad tiene que respetarlo.
            assertThat(s.getMotivoRechazo()).isNull();
        }

        @Test
        @DisplayName("aprobar dos veces es conflicto, no una operación sin efecto")
        void noSePuedeAprobarDosVeces() {
            SolicitudColaborador s = pendiente();
            s.aprobar(7L);

            // Si dos administradores abren la bandeja a la vez, el segundo tiene
            // que enterarse de que llegó tarde en vez de creer que hizo algo.
            assertThatThrownBy(() -> s.aprobar(9L))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("aprobada");
        }

        @Test
        @DisplayName("no se puede aprobar lo ya rechazado")
        void noSeReabreLoRechazado() {
            SolicitudColaborador s = pendiente();
            s.rechazar(7L, "El documento no corresponde a un RUC activo.");

            assertThatThrownBy(() -> s.aprobar(7L))
                    .isInstanceOf(ConflictoException.class);
        }
    }

    @Nested
    @DisplayName("rechazar")
    class Rechazar {

        @Test
        @DisplayName("guarda el motivo, que es lo que el solicitante verá")
        void guardaElMotivo() {
            SolicitudColaborador s = pendiente();
            s.rechazar(7L, "El documento no corresponde a un RUC activo en SUNAT.");

            assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
            assertThat(s.getMotivoRechazo()).contains("SUNAT");
            assertThat(s.getResueltaPor()).isEqualTo(7L);
            assertThat(s.getResueltaEn()).isNotNull();
        }

        @Test
        @DisplayName("no se puede rechazar lo ya aprobado")
        void noSeRevocaAprobando() {
            SolicitudColaborador s = pendiente();
            s.aprobar(7L);

            // Retirar el rol es otra operación (PATCH /usuarios/{id}/rol), no
            // una transición de la solicitud.
            assertThatThrownBy(() -> s.rechazar(7L, "Me arrepentí de aprobarla"))
                    .isInstanceOf(ConflictoException.class);
        }
    }

    @Nested
    @DisplayName("estados")
    class Estados {

        @Test
        @DisplayName("solo lo pendiente admite resolución")
        void soloPendienteSeResuelve() {
            assertThat(EstadoSolicitud.PENDIENTE.admiteResolucion()).isTrue();
            assertThat(EstadoSolicitud.APROBADA.admiteResolucion()).isFalse();
            assertThat(EstadoSolicitud.RECHAZADA.admiteResolucion()).isFalse();
        }

        @Test
        @DisplayName("solo lo pendiente está abierto")
        void soloPendienteEstaAbierta() {
            assertThat(EstadoSolicitud.PENDIENTE.estaAbierta()).isTrue();
            assertThat(EstadoSolicitud.APROBADA.estaAbierta()).isFalse();
            assertThat(EstadoSolicitud.RECHAZADA.estaAbierta()).isFalse();
        }
    }
}
