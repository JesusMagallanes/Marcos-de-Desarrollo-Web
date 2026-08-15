package com.backend.usuarios.colaborador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.usuarios.colaborador.dto.SolicitudDtos.Domicilio;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudRequest;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.correo.CorreoService;
import com.backend.usuarios.shared.error.ConflictoException;
import com.backend.usuarios.shared.error.DatosInvalidosException;
import com.backend.usuarios.shared.error.RecursoNoEncontradoException;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

/**
 * Las reglas del servicio de solicitudes.
 *
 * <p>Hasta ahora esto solo lo cubría la prueba de extremo a extremo, que hay que
 * lanzar a mano con la pila levantada. Lo que se comprueba aquí son las
 * decisiones que no se ven en la base ni en la entidad: que no se conceda el rol
 * sin documentos, que un documento no lo reclamen dos cuentas, y que subir dos
 * veces el mismo tipo sustituya en vez de acumular.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Solicitudes de colaborador")
class SolicitudServiceTest {

    @Mock
    private SolicitudRepository repositorio;
    @Mock
    private DocumentoRepository documentos;
    @Mock
    private UsuarioRepository usuarioRepositorio;
    @Mock
    private AlmacenDocumentos almacen;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private MetricasSeguridad metricas;
    @Mock
    private CorreoService correo;

    private SolicitudService servicio;

    private static final Long YO = 42L;
    private static final Long ADMIN = 1L;

    @BeforeEach
    void prepararServicio() {
        servicio = new SolicitudService(repositorio, documentos, usuarioRepositorio,
                almacen, auditoria, metricas, correo);
        // La versión de los términos llega por @Value, que no corre sin Spring.
        ReflectionTestUtils.setField(servicio, "terminosVigentes", "2026-08");
    }

    private Usuario usuario(Long id, String rol) {
        return Usuario.builder().id(id).name("Ana").lastname("Vega")
                .emailAddress("ana@t.com").rol(rol).build();
    }

    private SolicitudRequest peticionPersona() {
        return new SolicitudRequest(TipoPersona.NATURAL, TipoDocumento.DNI, "45678912",
                "Ana Vega Ríos", null, LocalDate.now().minusYears(30),
                "Taller de Ana", "987654321", "Accesorios",
                "Vendo accesorios para computadora que armo yo misma desde hace años.",
                new Domicilio("Jr. Unión 200", null, "Surco", "Lima", "Lima", "15039", "PE"),
                true, "2026-08");
    }

    private DocumentoIdentidad adjunto(TipoAdjunto tipo, Long id) {
        return DocumentoIdentidad.builder().id(id).usuarioId(YO).tipo(tipo)
                .nombreOriginal("foto.jpg").tipoMime("image/jpeg").tamanoBytes(1024L)
                .sha256("a".repeat(64)).ruta("2026/08/" + id + ".jpg")
                .subidoEn(Instant.now()).build();
    }

    /** Deja al usuario con los dos adjuntos que exige una persona natural. */
    private void conAdjuntosCompletos() {
        when(documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
                YO, TipoAdjunto.DOCUMENTO_ANVERSO))
                .thenReturn(Optional.of(adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 1L)));
        when(documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
                YO, TipoAdjunto.DOCUMENTO_REVERSO))
                .thenReturn(Optional.of(adjunto(TipoAdjunto.DOCUMENTO_REVERSO, 2L)));
    }

    @Nested
    @DisplayName("Al enviar la solicitud")
    class AlEnviar {

        @Test
        @DisplayName("no se crea si faltan archivos, y dice cuál")
        void faltanAdjuntos() {
            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(usuario(YO, "CLIENTE")));
            when(documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
                    anyLong(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crear(YO, peticionPersona()))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("Anverso")
                    .hasMessageContaining("Reverso");

            // Lo importante: no queda una solicitud a medias que luego haya que limpiar.
            verify(repositorio, never()).save(any());
        }

        @Test
        @DisplayName("un documento de otra cuenta se rechaza sin decir de quién es")
        void documentoDeOtro() {
            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(usuario(YO, "CLIENTE")));
            when(repositorio.existsByUsuarioIdAndEstado(YO, EstadoSolicitud.PENDIENTE))
                    .thenReturn(false);
            when(repositorio.existsPorDocumentoEnCurso("45678912", YO)).thenReturn(true);

            assertThatThrownBy(() -> servicio.crear(YO, peticionPersona()))
                    .isInstanceOf(ConflictoException.class)
                    // Ni el correo, ni el nombre, ni el id del otro titular.
                    .hasMessageNotContainingAny("@", "Ana", "id")
                    .hasMessageContaining("ya está asociado a otra solicitud");
        }

        @Test
        @DisplayName("quien ya es colaborador no vuelve a solicitar")
        void yaEsColaborador() {
            when(usuarioRepositorio.findById(YO))
                    .thenReturn(Optional.of(usuario(YO, "COLABORADOR")));

            assertThatThrownBy(() -> servicio.crear(YO, peticionPersona()))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("colaborador");
        }

        @Test
        @DisplayName("si los términos cambiaron, se pide leerlos otra vez")
        void terminosViejos() {
            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(usuario(YO, "CLIENTE")));

            SolicitudRequest p = peticionPersona();
            SolicitudRequest vieja = new SolicitudRequest(p.tipoPersona(), p.tipoDocumento(),
                    p.documento(), p.nombreTitular(), p.representanteLegal(), p.fechaNacimiento(),
                    p.nombreComercial(), p.telefonoContacto(), p.rubro(), p.descripcion(),
                    p.domicilio(), true, "2019-01");

            assertThatThrownBy(() -> servicio.crear(YO, vieja))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("cambiaron");
        }
    }

    @Nested
    @DisplayName("Al aprobar")
    class AlAprobar {

        private SolicitudColaborador pendienteDePersona() {
            return SolicitudColaborador.builder().id(7L).usuarioId(YO)
                    .tipoPersona(TipoPersona.NATURAL).tipoDocumento(TipoDocumento.DNI)
                    .documento("45678912").nombreTitular("Ana Vega Ríos")
                    .estado(EstadoSolicitud.PENDIENTE).creadaEn(Instant.now()).build();
        }

        @Test
        @DisplayName("NO se concede el rol si la solicitud no tiene los documentos")
        void sinDocumentosNoHayRol() {
            when(repositorio.findById(7L)).thenReturn(Optional.of(pendienteDePersona()));
            when(documentos.findBySolicitudIdOrderByTipoAsc(7L)).thenReturn(List.of());

            assertThatThrownBy(() -> servicio.aprobar(7L, ADMIN))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("falta comprobar la identidad");

            // Esta es la garantía de verdad: nadie se vuelve colaborador a ciegas.
            verify(usuarioRepositorio, never()).save(any());
            verify(metricas, never()).cambioRol(anyString());
        }

        @Test
        @DisplayName("tampoco si el documento se purgó y ya no se puede mirar")
        void documentoPurgado() {
            DocumentoIdentidad purgado = adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 1L);
            purgado.marcarPurgado();

            when(repositorio.findById(7L)).thenReturn(Optional.of(pendienteDePersona()));
            when(documentos.findBySolicitudIdOrderByTipoAsc(7L))
                    .thenReturn(List.of(purgado, adjunto(TipoAdjunto.DOCUMENTO_REVERSO, 2L)));

            assertThatThrownBy(() -> servicio.aprobar(7L, ADMIN))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("Anverso");
        }

        @Test
        @DisplayName("con todo en orden, cambia el rol y lo cuenta como cambio de rol")
        void apruebaYCuenta() {
            SolicitudColaborador solicitud = pendienteDePersona();
            Usuario ana = usuario(YO, "CLIENTE");

            when(repositorio.findById(7L)).thenReturn(Optional.of(solicitud));
            when(documentos.findBySolicitudIdOrderByTipoAsc(7L)).thenReturn(List.of(
                    adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 1L),
                    adjunto(TipoAdjunto.DOCUMENTO_REVERSO, 2L)));
            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(ana));
            when(repositorio.save(any())).thenReturn(solicitud);

            servicio.aprobar(7L, ADMIN);

            assertThat(ana.getRol()).isEqualTo("COLABORADOR");
            assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
            // La métrica es la misma que la del cambio de rol por el panel: si no,
            // el contador excluiría la vía por la que entran casi todos.
            verify(metricas).cambioRol("COLABORADOR");
        }
    }

    @Nested
    @DisplayName("Al subir un archivo")
    class AlSubir {

        private MockMultipartFile jpeg() {
            return new MockMultipartFile("archivo", "dni.jpg", "image/jpeg",
                    new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01 });
        }

        @Test
        @DisplayName("sustituye el anterior del mismo tipo en vez de acumularlo")
        void sustituyeElAnterior() {
            DocumentoIdentidad anterior = adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 1L);

            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(usuario(YO, "CLIENTE")));
            when(almacen.guardar(any())).thenReturn(
                    new AlmacenDocumentos.Guardado("2026/08/nuevo.jpg", "image/jpeg", 4L, "b".repeat(64)));
            when(documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
                    YO, TipoAdjunto.DOCUMENTO_ANVERSO)).thenReturn(Optional.of(anterior));
            when(documentos.save(any())).thenAnswer(i -> {
                DocumentoIdentidad d = i.getArgument(0);
                d.setId(9L);
                return d;
            });

            servicio.subir(YO, TipoAdjunto.DOCUMENTO_ANVERSO, jpeg());

            // Sin esto, una cuenta podía dejar archivos sueltos sin límite: con el
            // cupo general eran 1,5 GB por minuto, y los huérfanos no se purgan
            // hasta pasada una semana.
            verify(almacen).borrar("2026/08/1.jpg");
            verify(documentos).delete(anterior);
        }

        @Test
        @DisplayName("si no había ninguno, no intenta borrar nada")
        void primeraSubida() {
            when(usuarioRepositorio.findById(YO)).thenReturn(Optional.of(usuario(YO, "CLIENTE")));
            when(almacen.guardar(any())).thenReturn(
                    new AlmacenDocumentos.Guardado("2026/08/uno.jpg", "image/jpeg", 4L, "c".repeat(64)));
            when(documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(
                    YO, TipoAdjunto.DOCUMENTO_ANVERSO)).thenReturn(Optional.empty());
            when(documentos.save(any())).thenAnswer(i -> {
                DocumentoIdentidad d = i.getArgument(0);
                d.setId(9L);
                return d;
            });

            servicio.subir(YO, TipoAdjunto.DOCUMENTO_ANVERSO, jpeg());

            verify(almacen, never()).borrar(anyString());
            verify(documentos, never()).delete(any());
        }

        @Test
        @DisplayName("quien ya es colaborador no sube nada")
        void colaboradorNoSube() {
            when(usuarioRepositorio.findById(YO))
                    .thenReturn(Optional.of(usuario(YO, "COLABORADOR")));

            assertThatThrownBy(() -> servicio.subir(YO, TipoAdjunto.DOCUMENTO_ANVERSO, jpeg()))
                    .isInstanceOf(ConflictoException.class);

            // No se llega ni a tocar el disco.
            verify(almacen, never()).guardar(any());
        }
    }

    @Nested
    @DisplayName("Al descargar un archivo")
    class AlDescargar {

        @Test
        @DisplayName("a un tercero se le responde 404, no 403")
        void terceroRecibe404() {
            when(documentos.findById(5L))
                    .thenReturn(Optional.of(adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 5L)));

            // Un 403 confirmaría que ese id existe, y probando números se sabría
            // cuántos documentos hay y de quién.
            assertThatThrownBy(() -> servicio.descargar(5L, 99L, false))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(almacen, never()).leer(anyString());
        }

        @Test
        @DisplayName("el administrador sí puede, y queda auditado")
        void adminPuede() {
            DocumentoIdentidad doc = adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 5L);
            when(documentos.findById(5L)).thenReturn(Optional.of(doc));
            when(almacen.existe(doc.getRuta())).thenReturn(true);
            when(almacen.leer(doc.getRuta())).thenReturn(new byte[] { 1, 2, 3 });
            when(usuarioRepositorio.findById(ADMIN))
                    .thenReturn(Optional.of(usuario(ADMIN, "ADMINISTRADOR")));

            var descarga = servicio.descargar(5L, ADMIN, true);

            assertThat(descarga.contenido()).hasSize(3);
            // "Quién miró el DNI de quién" tiene que quedar registrado.
            verify(auditoria).registrar(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("un documento ya purgado no se puede recuperar")
        void purgadoNoSeDescarga() {
            DocumentoIdentidad doc = adjunto(TipoAdjunto.DOCUMENTO_ANVERSO, 5L);
            doc.marcarPurgado();
            when(documentos.findById(5L)).thenReturn(Optional.of(doc));
            lenient().when(almacen.existe(anyString())).thenReturn(false);

            assertThatThrownBy(() -> servicio.descargar(5L, YO, false))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("retención");
        }
    }

    @Nested
    @DisplayName("La bandeja del administrador")
    class Bandeja {

        @Test
        @DisplayName("no lanza una consulta por fila para traer los solicitantes")
        void sinNMasUno() {
            List<SolicitudColaborador> muchas = List.of(
                    SolicitudColaborador.builder().id(1L).usuarioId(10L)
                            .tipoPersona(TipoPersona.NATURAL).estado(EstadoSolicitud.PENDIENTE)
                            .creadaEn(Instant.now()).build(),
                    SolicitudColaborador.builder().id(2L).usuarioId(11L)
                            .tipoPersona(TipoPersona.NATURAL).estado(EstadoSolicitud.PENDIENTE)
                            .creadaEn(Instant.now()).build());

            when(repositorio.findByEstadoOrderByCreadaEnDesc(EstadoSolicitud.PENDIENTE))
                    .thenReturn(muchas);
            when(usuarioRepositorio.findAllById(any()))
                    .thenReturn(List.of(usuario(10L, "CLIENTE"), usuario(11L, "CLIENTE")));
            when(documentos.findBySolicitudIdIn(any())).thenReturn(List.of());

            assertThat(servicio.listar(EstadoSolicitud.PENDIENTE)).hasSize(2);

            // Un findById por solicitud es justo lo que se quiere evitar: con 50
            // en la bandeja serían 51 consultas.
            verify(usuarioRepositorio, never()).findById(anyLong());
        }

        @Test
        @DisplayName("si el solicitante desapareció, falla en vez de callarse")
        void solicitanteQueNoExiste() {
            when(repositorio.findByEstadoOrderByCreadaEnDesc(EstadoSolicitud.PENDIENTE))
                    .thenReturn(List.of(SolicitudColaborador.builder().id(1L).usuarioId(99L)
                            .tipoPersona(TipoPersona.NATURAL).estado(EstadoSolicitud.PENDIENTE)
                            .creadaEn(Instant.now()).build()));
            when(usuarioRepositorio.findAllById(any())).thenReturn(List.of());
            when(documentos.findBySolicitudIdIn(any())).thenReturn(List.of());

            // Saltar la fila en silencio dejaría una bandeja incompleta sin que
            // nadie se entere.
            assertThatThrownBy(() -> servicio.listar(EstadoSolicitud.PENDIENTE))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("una bandeja vacía no consulta usuarios ni adjuntos")
        void bandejaVacia() {
            when(repositorio.findByEstadoOrderByCreadaEnDesc(EstadoSolicitud.APROBADA))
                    .thenReturn(List.of());

            assertThatCode(() -> assertThat(servicio.listar(EstadoSolicitud.APROBADA)).isEmpty())
                    .doesNotThrowAnyException();

            verify(usuarioRepositorio, never()).findAllById(any());
        }
    }
}
