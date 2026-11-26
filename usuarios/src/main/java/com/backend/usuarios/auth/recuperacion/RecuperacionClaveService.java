package com.backend.usuarios.auth.recuperacion;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.shared.correo.CorreoService;
import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;
import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recuperar la contraseña con un código de 6 dígitos enviado por correo.
 *
 * <h4>No revela qué correos tienen cuenta</h4>
 *
 * <p>{@link #solicitar} no devuelve nada ni lanza nada: la respuesta es la
 * misma exista o no la cuenta. Y el correo sale en segundo plano DESPUÉS de
 * responder, porque un envío SMTP tarda un segundo y, si fuera dentro de la
 * petición, el tiempo de respuesta diría lo que el cuerpo calla.
 *
 * <h4>Fuerza bruta</h4>
 *
 * <p>Seis dígitos son un millón de combinaciones. Lo que las hace inalcanzables
 * es que cada código admite {@value #MAXIMO_INTENTOS} intentos y caduca en
 * quince minutos, que pedir códigos tiene cupo por correo y por IP, y que
 * probar códigos tiene el suyo por IP (ver RateLimitFilter).
 *
 * <h4>Al cambiar la clave se cortan todas las sesiones</h4>
 *
 * <p>Quien recupera la contraseña suele hacerlo porque la perdió o porque cree
 * que otro la tiene. Si las sesiones abiertas siguieran vivas, el que la tenía
 * seguiría dentro siete días renovando su refresh token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecuperacionClaveService {

    static final int MAXIMO_INTENTOS = 5;
    static final Duration VIGENCIA = Duration.ofMinutes(15);

    /** Códigos que se pueden pedir para un mismo correo por hora. */
    static final int MAXIMO_POR_CORREO = 3;
    static final Duration VENTANA_POR_CORREO = Duration.ofHours(1);

    /**
     * Intentos de canje por correo. El cupo por IP (RateLimitFilter) se esquiva
     * repartiendo las peticiones entre muchas IP; este no, porque cuenta sobre
     * la cuenta atacada.
     */
    static final int MAXIMO_CANJES_POR_CORREO = 10;
    static final Duration VENTANA_CANJES_POR_CORREO = Duration.ofMinutes(15);

    private static final SecureRandom AZAR = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final CodigoRecuperacionRepository codigos;
    private final PasswordEncoder codificador;
    private final CorreoService correo;
    private final AuditoriaService auditoria;
    private final LimitadorPeticiones limitador;

    /** Hilo virtual por envío: el correo nunca retiene la respuesta. */
    private final Executor envio = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Hash con el que se compara cuando la cuenta no existe, para que esa
     * respuesta tarde lo mismo que la de un código equivocado. Se calcula una
     * vez: BCrypt es lento a propósito.
     */
    private volatile String hashDeRelleno;

    /**
     * Emite un código y lo envía, si la cuenta existe y entra con contraseña.
     * Nunca dice si existe.
     */
    @Transactional
    public void solicitar(String emailEnBruto) {
        String email = Saneador.normalizarEmail(emailEnBruto);

        // El cupo por correo va antes de mirar si la cuenta existe, para que
        // también agotarlo se comporte igual en los dos casos. Lo que acota es
        // el buzón de la víctima: el de la IP no basta, porque se puede pedir
        // desde muchas.
        if (!limitador.permitir("recuperar|" + email, MAXIMO_POR_CORREO, VENTANA_POR_CORREO)) {
            log.warn("Cupo de códigos de recuperación agotado para un correo");
            return;
        }

        Usuario usuario = usuarios.findByEmailAddress(email).orElse(null);
        if (usuario == null || !usuario.puedeIniciarSesionConPassword()) {
            // El camino con cuenta calcula un BCrypt (el del código), que es lo
            // que más tarda. Sin este de relleno, «sin cuenta» respondía en 10 ms
            // y «con cuenta» en 300: el 202 callaba y el cronómetro hablaba.
            codificador.encode("relleno-" + AZAR.nextInt());
        }
        if (usuario == null) {
            auditoria.registrarFallo(Evento.RECUPERACION_SOLICITADA, email, "correo sin cuenta");
            return;
        }

        if (!usuario.puedeIniciarSesionConPassword()) {
            // Cuenta de Google o Facebook: no tiene contraseña que recuperar,
            // pero sí merece saber por qué no le llega un código.
            auditoria.registrar(Evento.RECUPERACION_SOLICITADA, email,
                    "cuenta " + usuario.getProveedor() + ", sin contraseña local");
            String proveedor = usuario.getProveedor().name();
            trasConfirmar(() -> correo.recuperacionConProveedor(email, proveedor));
            return;
        }

        Instant ahora = Instant.now();

        // Solo vale el último código que llegó al buzón.
        for (CodigoRecuperacion anterior : codigos.findByUsuarioIdAndUsadoEnIsNull(usuario.getId())) {
            anterior.setUsadoEn(ahora);
        }

        String codigo = "%06d".formatted(AZAR.nextInt(1_000_000));
        codigos.save(CodigoRecuperacion.builder()
                .usuarioId(usuario.getId())
                .codigoHash(codificador.encode(codigo))
                .expiraEn(ahora.plus(VIGENCIA))
                .creadoEn(ahora)
                .build());

        auditoria.registrar(Evento.RECUPERACION_SOLICITADA, email, "código emitido");
        String nombre = usuario.getName();
        long minutos = VIGENCIA.toMinutes();
        trasConfirmar(() -> correo.codigoRecuperacion(email, nombre, codigo, minutos));
    }

    /**
     * Cambia la contraseña si el código es el vigente.
     *
     * <p>Devuelve {@code false} en vez de lanzar, y no es un capricho: el fallo
     * tiene que GUARDAR el intento gastado. Una excepción desharía la
     * transacción y, con ella, el contador; la fuerza bruta saldría gratis.
     * Quien llama traduce el {@code false} a un 400 ya fuera de la transacción.
     */
    @Transactional
    public boolean restablecer(String emailEnBruto, String codigo, String nuevaClave) {
        String email = Saneador.normalizarEmail(emailEnBruto);
        Instant ahora = Instant.now();

        if (!limitador.permitir("restablecer|" + email, MAXIMO_CANJES_POR_CORREO, VENTANA_CANJES_POR_CORREO)) {
            codificador.matches(codigo, hashDeRelleno());
            auditoria.registrarFallo(Evento.CLAVE_RESTABLECIDA, email, "cupo de intentos por correo agotado");
            return false;
        }

        Usuario usuario = usuarios.findByEmailAddress(email)
                .filter(Usuario::puedeIniciarSesionConPassword)
                .orElse(null);

        CodigoRecuperacion vigente = usuario == null ? null
                : codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(usuario.getId())
                        .filter(c -> c.vigente(ahora, MAXIMO_INTENTOS))
                        .orElse(null);

        if (vigente == null) {
            // Se compara igual, contra un hash de relleno: sin esto, «no hay
            // código» respondería en microsegundos y «código equivocado» en lo
            // que tarda BCrypt, y la diferencia diría qué correos tienen cuenta.
            codificador.matches(codigo, hashDeRelleno());
            auditoria.registrarFallo(Evento.CLAVE_RESTABLECIDA, email, "sin código vigente");
            return false;
        }

        if (!codificador.matches(codigo, vigente.getCodigoHash())) {
            vigente.setIntentos(vigente.getIntentos() + 1);
            auditoria.registrarFallo(Evento.CLAVE_RESTABLECIDA, email,
                    "código incorrecto, intento " + vigente.getIntentos() + " de " + MAXIMO_INTENTOS);
            return false;
        }

        vigente.setUsadoEn(ahora);
        usuario.setPassword(codificador.encode(nuevaClave));
        usuario.setTokensValidosDesde(ahora);

        // Con la clave nueva, los intentos fallidos de login de antes ya no
        // significan nada: se libera el cupo para que pueda entrar ya.
        limitador.limpiar("login|" + email);
        auditoria.registrar(Evento.CLAVE_RESTABLECIDA, email,
                "contraseña cambiada; se cortaron las sesiones abiertas");
        return true;
    }

    private String hashDeRelleno() {
        String hash = hashDeRelleno;
        if (hash == null) {
            hash = codificador.encode("relleno-" + AZAR.nextLong());
            hashDeRelleno = hash;
        }
        return hash;
    }

    /**
     * El correo sale cuando la transacción ya se confirmó, y en otro hilo.
     *
     * <p>Después de confirmar, porque mandar un código que luego no queda
     * guardado sería mandar uno que no funciona. En otro hilo, para que el
     * tiempo de respuesta no dependa de si hubo correo (ver la cabecera).
     */
    private void trasConfirmar(Runnable aviso) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.execute(aviso);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.execute(aviso);
            }
        });
    }
}
