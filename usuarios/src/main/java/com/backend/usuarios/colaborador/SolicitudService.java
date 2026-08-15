package com.backend.usuarios.colaborador;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.multipart.MultipartFile;

import com.backend.usuarios.colaborador.dto.SolicitudDtos.RechazoRequest;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudAdminResponse;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudRequest;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SolicitudResponse;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.SubidaResponse;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.shared.correo.CorreoService;
import com.backend.usuarios.shared.error.ConflictoException;
import com.backend.usuarios.shared.error.DatosInvalidosException;
import com.backend.usuarios.shared.error.RecursoNoEncontradoException;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Solicitudes para vender en la tienda, con verificación de identidad.
 *
 * <p>Aprobar hace dos cosas que tienen que ocurrir juntas o no ocurrir: marcar
 * la solicitud y cambiar el rol del usuario. Por eso el método es transaccional
 * y no llama al servicio de usuarios, que abriría su propia transacción.
 *
 * <p>El recorrido de los adjuntos es en dos tiempos a propósito: se suben según
 * se eligen en el formulario y quedan sueltos, y al enviar la solicitud los
 * reclama. Mandarlo todo junto obligaría a volver a subir varios megas de fotos
 * cada vez que un campo de texto no pasa la validación.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SolicitudService {

    /*
     * Los roles dejaron de ser un enum y pasaron a ser filas de la tabla `rol`,
     * editables desde el panel. Se pierde la comprobación del compilador, así
     * que al menos los nombres viven en un sitio y no repartidos como literales.
     * Los dos son roles de sistema: RolSeeder los siembra y no se pueden borrar.
     */
    private static final String ROL_CLIENTE = "CLIENTE";
    private static final String ROL_COLABORADOR = "COLABORADOR";

    private final SolicitudRepository repositorio;
    private final DocumentoRepository documentos;
    private final UsuarioRepository usuarioRepositorio;
    private final AlmacenDocumentos almacen;
    private final AuditoriaService auditoria;
    private final MetricasSeguridad metricas;
    private final CorreoService correo;

    /**
     * Versión vigente de los términos. Se compara con la que dice el cliente
     * haber enseñado: si no coincide, es que tenía el formulario abierto desde
     * antes del cambio y estaría aceptando algo que no leyó.
     */
    @Value("${smartzone.colaboradores.terminos-version:2026-08}")
    private String terminosVigentes;

    /* ── Adjuntos ── */

    /**
     * Guarda un documento y lo deja a nombre del usuario, todavía sin solicitud.
     *
     * <p><b>Sustituye al anterior del mismo tipo</b> en vez de acumularlo. Es lo
     * que espera quien sube el reverso, lo ve borroso y vuelve a subirlo: que
     * valga el último, no que le den un error ni que se guarden los dos.
     *
     * <p>Y es además lo que impide llenar el disco. Sin sustituir, nada acotaba
     * cuántos archivos sueltos podía dejar una cuenta: con el cupo general de
     * 300 peticiones por minuto y 5 MB cada una, son 1,5 GB por minuto, y los
     * huérfanos no se purgan hasta pasada una semana. Ahora un usuario ocupa a lo
     * sumo un archivo por tipo, y el índice único parcial de la migración lo
     * garantiza aunque dos peticiones lleguen a la vez.
     */
    @Transactional
    public SubidaResponse subir(Long usuarioId, TipoAdjunto tipo, MultipartFile fichero) {
        Usuario usuario = buscarUsuario(usuarioId);

        if (!ROL_CLIENTE.equals(usuario.getRol())) {
            throw new ConflictoException(
                    "Tu cuenta ya tiene permisos de %s".formatted(usuario.getRol().toLowerCase()));
        }

        AlmacenDocumentos.Guardado guardado = almacen.guardar(fichero);
        descartarAnterior(usuarioId, tipo);

        DocumentoIdentidad documento = DocumentoIdentidad.builder()
                .usuarioId(usuarioId)
                .tipo(tipo)
                .nombreOriginal(nombreVisible(fichero.getOriginalFilename()))
                .tipoMime(guardado.tipoMime())
                .tamanoBytes(guardado.tamano())
                .sha256(guardado.sha256())
                .ruta(guardado.ruta())
                .subidoEn(Instant.now())
                .build();

        DocumentoIdentidad creado = documentos.save(documento);

        auditoria.registrar(Evento.DOCUMENTO_SUBIDO, usuario.getEmailAddress(),
                "documento=%d tipo=%s bytes=%d".formatted(creado.getId(), tipo, guardado.tamano()));
        metricas.documento("subido");

        return SubidaResponse.desde(creado);
    }

    /** Lo necesario para devolver un adjunto por HTTP. */
    public record Descarga(byte[] contenido, String tipoMime, String nombre) {
    }

    /**
     * Entrega los bytes de un adjunto a quien tenga derecho a verlos.
     *
     * <p>El permiso se comprueba aquí y no en el controlador porque es la regla
     * que de verdad protege estos ficheros: son el dato más sensible del
     * sistema y el servicio de usuarios no tiene RLS que respalde el fallo.
     */
    public Descarga descargar(Long documentoId, Long solicitanteId, boolean esAdministrador) {
        DocumentoIdentidad documento = documentos.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Documento " + documentoId + " no encontrado"));

        boolean esDueno = documento.getUsuarioId().equals(solicitanteId);
        if (!esDueno && !esAdministrador) {
            // Mismo 404 que si no existiera. Un 403 confirmaría que ese id
            // existe, y probando números se sabría cuántos documentos hay y de
            // quién: eso ya es información que no le corresponde a quien pregunta.
            throw new RecursoNoEncontradoException("Documento " + documentoId + " no encontrado");
        }

        if (documento.estaPurgado() || !almacen.existe(documento.getRuta())) {
            throw new RecursoNoEncontradoException(
                    "El documento ya no está disponible: se eliminó por política de retención");
        }

        Usuario quienPide = buscarUsuario(solicitanteId);
        auditoria.registrar(Evento.DOCUMENTO_DESCARGADO, quienPide.getEmailAddress(),
                "documento=%d duenio=%d admin=%s"
                        .formatted(documentoId, documento.getUsuarioId(), esAdministrador));

        metricas.documento("descargado");

        return new Descarga(almacen.leer(documento.getRuta()), documento.getTipoMime(),
                documento.getNombreOriginal());
    }

    /* ── Solicitante ── */

    @Transactional
    public SolicitudResponse crear(Long usuarioId, SolicitudRequest dto) {
        Usuario usuario = buscarUsuario(usuarioId);

        // Quien ya es colaborador (o más) no tiene nada que solicitar.
        if (!ROL_CLIENTE.equals(usuario.getRol())) {
            throw new ConflictoException(
                    "Tu cuenta ya tiene permisos de %s".formatted(usuario.getRol().toLowerCase()));
        }

        dto.validarCoherencia();
        exigirTerminosVigentes(dto.terminosVersion());

        if (repositorio.existsByUsuarioIdAndEstado(usuarioId, EstadoSolicitud.PENDIENTE)) {
            throw new ConflictoException("Ya tienes una solicitud pendiente de revisión");
        }

        // Un documento identifica a UN titular. Si otra cuenta lo tiene en curso
        // o aprobado, o es un error de tecleo o alguien está intentando colgarse
        // de un negocio ajeno; en los dos casos lo revisa una persona.
        //
        // No se dice de quién es: confirmar que ese documento ya está en el
        // sistema, y a nombre de quién, sería filtrar datos de otro cliente.
        if (repositorio.existsPorDocumentoEnCurso(dto.documento(), usuarioId)) {
            throw new ConflictoException(
                    "Ese documento ya está asociado a otra solicitud. "
                    + "Si es un error, escríbenos desde los canales de atención.");
        }

        // Se resuelven ANTES de guardar nada: si faltan adjuntos, la solicitud
        // no llega a existir y el usuario no se queda con una a medias que
        // tendría que cancelar.
        Map<TipoAdjunto, DocumentoIdentidad> adjuntos = reunirAdjuntos(usuarioId, dto.tipoPersona());

        SolicitudColaborador solicitud = SolicitudColaborador.builder()
                .usuarioId(usuarioId)
                .tipoPersona(dto.tipoPersona())
                .tipoDocumento(dto.tipoDocumento())
                .documento(dto.documento())
                .nombreTitular(dto.nombreTitular())
                .representanteLegal(vacioComoNulo(dto.representanteLegal()))
                .fechaNacimiento(dto.fechaNacimiento())
                .nombreComercial(dto.nombreComercial())
                .telefonoContacto(dto.telefonoContacto())
                .rubro(dto.rubro())
                .descripcion(dto.descripcion())
                .direccion(dto.domicilio().direccion())
                .referencia(vacioComoNulo(dto.domicilio().referencia()))
                .distrito(dto.domicilio().distrito())
                .provincia(dto.domicilio().provincia())
                .departamento(dto.domicilio().departamento())
                .codigoPostal(dto.domicilio().codigoPostal())
                .pais(dto.domicilio().pais())
                .latitud(dto.domicilio().latitud())
                .longitud(dto.domicilio().longitud())
                .terminosVersion(dto.terminosVersion())
                .terminosAceptadosEn(Instant.now())
                .estado(EstadoSolicitud.PENDIENTE)
                .creadaEn(Instant.now())
                .build();

        SolicitudColaborador guardada;
        try {
            guardada = repositorio.save(solicitud);
            // Se fuerza el volcado aquí para que el choque contra el índice
            // único salte dentro del try y no al cerrar la transacción, donde ya
            // no se podría traducir a un mensaje entendible.
            repositorio.flush();
        } catch (DataIntegrityViolationException ex) {
            // Dos peticiones simultáneas: ambas pasaron la comprobación de
            // arriba y el índice único parcial paró a la segunda.
            throw new ConflictoException("Ya tienes una solicitud pendiente de revisión");
        }

        List<DocumentoIdentidad> asignados = new ArrayList<>();
        for (DocumentoIdentidad documento : adjuntos.values()) {
            documento.asignarA(guardada.getId());
            asignados.add(documentos.save(documento));
        }

        auditoria.registrar(Evento.SOLICITUD_COLABORADOR, usuario.getEmailAddress(),
                "solicitud=%d comercio=%s tipo=%s adjuntos=%d".formatted(
                        guardada.getId(), dto.nombreComercial(), dto.tipoPersona(), asignados.size()));

        return SolicitudResponse.desde(guardada, asignados);
    }

    /** La última del usuario, o vacío si nunca envió ninguna. */
    public Optional<SolicitudResponse> mia(Long usuarioId) {
        return repositorio.findFirstByUsuarioIdOrderByCreadaEnDesc(usuarioId)
                .map(s -> SolicitudResponse.desde(s, documentos.findBySolicitudIdOrderByTipoAsc(s.getId())));
    }

    /* ── Administrador ── */

    public List<SolicitudAdminResponse> listar(EstadoSolicitud estado) {
        List<SolicitudColaborador> solicitudes = estado == null
                ? repositorio.findAllByOrderByEstadoAscCreadaEnDesc()
                : repositorio.findByEstadoOrderByCreadaEnDesc(estado);

        if (solicitudes.isEmpty()) {
            return List.of();
        }

        // Los solicitantes se traen de una vez y no uno por uno dentro del
        // bucle: con 50 solicitudes en la bandeja, la versión ingenua lanzaba
        // 51 consultas. La solicitud guarda `usuario_id` plano y no una
        // relación JPA (es la frontera del servicio), así que el JOIN FETCH no
        // está disponible y hay que resolverlo así.
        Map<Long, Usuario> porId = usuarioRepositorio
                .findAllById(solicitudes.stream().map(SolicitudColaborador::getUsuarioId).toList())
                .stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));

        // Y los adjuntos igual: una consulta para todos, agrupados en memoria.
        Map<Long, List<DocumentoIdentidad>> adjuntosPorSolicitud = documentos
                .findBySolicitudIdIn(solicitudes.stream().map(SolicitudColaborador::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(DocumentoIdentidad::getSolicitudId));

        return solicitudes.stream()
                .map(s -> SolicitudAdminResponse.desde(s, exigirUsuario(porId, s.getUsuarioId()),
                        adjuntosPorSolicitud.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    /**
     * Aprueba y asciende al usuario a COLABORADOR.
     *
     * <p>OJO con lo que NO hace: no toca los tokens ya emitidos. El rol viaja
     * dentro del JWT, así que el usuario seguirá siendo cliente para el backend
     * hasta que renueve. No se revocan sus tokens a propósito, porque eso le
     * obligaría a volver a escribir la contraseña; en su lugar,
     * {@code POST /api/auth/refresh} ya emite un token con el rol nuevo porque
     * relee al usuario de la base. El cliente solo tiene que refrescar cuando
     * vea su solicitud aprobada — está escrito en docs/contrato-colaboradores.md.
     */
    @Transactional
    public SolicitudAdminResponse aprobar(Long solicitudId, Long administradorId) {
        SolicitudColaborador solicitud = buscar(solicitudId);

        // Nadie se hace colaborador sin documentos revisables. La comprobación
        // vive aquí y no solo en el alta porque es la que de verdad importa:
        // vale para las solicitudes anteriores a la verificación de identidad y
        // para cualquiera que llegue por una vía que no se haya previsto.
        exigirAdjuntosCompletos(solicitud);

        solicitud.aprobar(administradorId);

        Usuario usuario = buscarUsuario(solicitud.getUsuarioId());
        String anterior = usuario.getRol();
        usuario.setRol(ROL_COLABORADOR);
        usuarioRepositorio.save(usuario);

        auditoria.registrar(Evento.COLABORADOR_APROBADO, usuario.getEmailAddress(),
                "solicitud=%d rol=%s->%s por=%d"
                        .formatted(solicitudId, anterior, ROL_COLABORADOR, administradorId));

        // Esto es un cambio de rol tanto como el de PATCH /usuarios/{id}/rol, y
        // tiene que contar en el mismo sitio. Si no, el panel enseña una cifra
        // de "cambios de rol" que no incluye la vía por la que van a entrar casi
        // todos los colaboradores, y nadie sospecharía que falta la mitad.
        metricas.cambioRol(ROL_COLABORADOR);

        return SolicitudAdminResponse.desde(repositorio.save(solicitud), usuario,
                documentos.findBySolicitudIdOrderByTipoAsc(solicitudId));
    }

    @Transactional
    public SolicitudAdminResponse rechazar(Long solicitudId, Long administradorId, RechazoRequest dto) {
        SolicitudColaborador solicitud = buscar(solicitudId);
        solicitud.rechazar(administradorId, dto.motivo());

        Usuario usuario = buscarUsuario(solicitud.getUsuarioId());

        auditoria.registrar(Evento.COLABORADOR_RECHAZADO, usuario.getEmailAddress(),
                "solicitud=%d por=%d".formatted(solicitudId, administradorId));

        avisarTrasConfirmar(() -> correo.solicitudRechazada(
                usuario.getEmailAddress(), solicitud.getNombreComercial(), dto.motivo()));

        return SolicitudAdminResponse.desde(repositorio.save(solicitud), usuario,
                documentos.findBySolicitudIdOrderByTipoAsc(solicitudId));
    }

    /* ── Apoyo ── */

    /**
     * Quita el archivo suelto que el usuario tuviera de ese mismo tipo.
     *
     * <p>Solo toca los que aún no ha enviado con ninguna solicitud: los ya
     * asignados son parte de una revisión y no se pisan nunca, ni siquiera si el
     * usuario vuelve a subir tras un rechazo (esos se conservan como traza y la
     * solicitud nueva estrena los suyos).
     *
     * <p>Se borra el fichero y la ficha, no se marca purgado: nadie llegó a
     * verlo, así que no hay verificación que documentar.
     */
    private void descartarAnterior(Long usuarioId, TipoAdjunto tipo) {
        documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(usuarioId, tipo)
                .ifPresent(anterior -> {
                    almacen.borrar(anterior.getRuta());
                    documentos.delete(anterior);
                    // Se fuerza el borrado antes de insertar el nuevo: si no,
                    // Hibernate ordena el INSERT antes del DELETE al cerrar la
                    // transacción y choca contra el índice único.
                    documentos.flush();
                });
    }

    /**
     * Busca entre los adjuntos sueltos del usuario los que exige su tipo de
     * persona, quedándose con el más reciente de cada tipo.
     *
     * @throws DatosInvalidosException si falta alguno, diciendo cuáles
     */
    private Map<TipoAdjunto, DocumentoIdentidad> reunirAdjuntos(Long usuarioId, TipoPersona tipoPersona) {
        Map<TipoAdjunto, DocumentoIdentidad> encontrados = new EnumMap<>(TipoAdjunto.class);
        List<String> faltan = new ArrayList<>();

        for (TipoAdjunto tipo : tipoPersona.adjuntosExigidos()) {
            documentos.findFirstByUsuarioIdAndTipoAndSolicitudIdIsNullOrderBySubidoEnDesc(usuarioId, tipo)
                    .ifPresentOrElse(d -> encontrados.put(tipo, d), () -> faltan.add(tipo.etiqueta()));
        }

        if (!faltan.isEmpty()) {
            throw new DatosInvalidosException(
                    "Antes de enviar la solicitud faltan estos archivos: " + String.join(", ", faltan));
        }
        return encontrados;
    }

    /** Los adjuntos que la solicitud debería tener, según su tipo de persona. */
    private void exigirAdjuntosCompletos(SolicitudColaborador solicitud) {
        List<DocumentoIdentidad> presentes = documentos
                .findBySolicitudIdOrderByTipoAsc(solicitud.getId());

        List<String> faltan = solicitud.getTipoPersona().adjuntosExigidos().stream()
                .filter(tipo -> presentes.stream().noneMatch(d -> d.getTipo() == tipo && !d.estaPurgado()))
                .map(TipoAdjunto::etiqueta)
                .toList();

        if (!faltan.isEmpty()) {
            throw new ConflictoException(
                    "No se puede aprobar: falta comprobar la identidad. No hay "
                            + String.join(", ", faltan)
                            + ". Recházala pidiéndole que los vuelva a enviar.");
        }
    }

    private void exigirTerminosVigentes(String version) {
        if (!terminosVigentes.equals(version)) {
            throw new DatosInvalidosException(
                    "Los términos cambiaron mientras rellenabas el formulario. "
                            + "Vuelve a leerlos y acéptalos para continuar.");
        }
    }

    /**
     * El nombre que enseña la bandeja. Se sanea y se recorta porque es texto que
     * eligió quien subió el archivo: no construye rutas, pero sí se pinta.
     */
    private static String nombreVisible(String original) {
        String limpio = Saneador.texto(original);
        if (limpio == null || limpio.isBlank()) {
            return "documento";
        }
        return limpio.length() > 255 ? limpio.substring(0, 255) : limpio;
    }

    private static String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private SolicitudColaborador buscar(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud " + id + " no encontrada"));
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no encontrado"));
    }

    /**
     * El solicitante tiene que estar en el lote traído por {@link #listar}. Si
     * falta, la base quedó inconsistente: la clave foránea es
     * {@code ON DELETE CASCADE}, así que borrar al usuario debería haberse
     * llevado su solicitud por delante. Se falla en vez de saltar la fila en
     * silencio, que dejaría una bandeja incompleta sin que nadie se entere.
     */
    private Usuario exigirUsuario(Map<Long, Usuario> porId, Long usuarioId) {
        Usuario usuario = porId.get(usuarioId);
        if (usuario == null) {
            throw new RecursoNoEncontradoException(
                    "La solicitud apunta al usuario " + usuarioId + ", que ya no existe");
        }
        return usuario;
    }

    /**
     * Manda el aviso solo si la transacción llega a confirmarse.
     *
     * <p>Si se enviara sin esperar, un fallo posterior desharía la aprobación en
     * la base pero el correo ya estaría en el buzón del solicitante: se le habría
     * dicho que puede vender cuando no es verdad, y un correo no se puede
     * deshacer.
     *
     * <p>Fuera de una transacción se envía directamente, para que esto siga
     * funcionando si alguna vez se llama desde otro sitio.
     */
    private void avisarTrasConfirmar(Runnable aviso) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aviso.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aviso.run();
            }
        });
    }
}
