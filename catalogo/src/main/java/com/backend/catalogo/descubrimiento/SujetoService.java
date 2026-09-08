package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve QUIÉN está explorando, y funde el rastro anónimo con la cuenta.
 *
 * <h4>La regla de seguridad que no se negocia</h4>
 *
 * <p>Si hay sesión iniciada, el sujeto sale del JWT y el identificador que
 * mande el cliente SE IGNORA. Solo se acepta el del cliente cuando no hay
 * cuenta detrás. Sin esa regla, cualquiera podría leer el perfil de otro con
 * solo poner su identificador en una cabecera.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class SujetoService {

    private final SujetoRepository repositorio;
    private final PesosDescubrimiento pesos;

    /**
     * El sujeto de esta petición.
     *
     * @param usuarioId   del JWT; {@code null} si nadie inició sesión
     * @param sujetoDelCliente el que dice el navegador; solo vale si no hay cuenta
     */
    @Transactional
    public Sujeto resolver(Long usuarioId, UUID sujetoDelCliente, String ubigeo) {
        Sujeto sujeto = usuarioId != null
                ? deUsuario(usuarioId, sujetoDelCliente)
                : anonimo(sujetoDelCliente);

        sujeto.setVistoEn(Instant.now());
        if (ubigeo != null && !ubigeo.isBlank()) {
            sujeto.setUbigeo(ubigeo);
        }
        return repositorio.save(sujeto);
    }

    private Sujeto deUsuario(Long usuarioId, UUID sujetoDelCliente) {
        Sujeto cuenta = repositorio.buscarVivoDeUsuario(usuarioId)
                .orElseGet(() -> repositorio.save(nuevo(usuarioId)));

        /*
         * Aquí ocurre la fusión anónimo → autenticado.
         *
         * El caso real: alguien navega el lunes sin cuenta, mira veinte
         * productos y aplica filtros; el martes inicia sesión. Sin esto, todo
         * ese aprendizaje se tira y la cuenta empieza de cero.
         */
        if (sujetoDelCliente != null && !sujetoDelCliente.equals(cuenta.getId())) {
            repositorio.findById(sujetoDelCliente)
                    .filter(s -> !s.estaFusionado())
                    .filter(s -> s.getUsuarioId() == null)
                    .ifPresent(anonimo -> fusionar(anonimo, cuenta));
        }
        return cuenta;
    }

    private Sujeto anonimo(UUID sujetoDelCliente) {
        if (sujetoDelCliente == null) {
            return repositorio.save(nuevo(null));
        }

        Sujeto encontrado = repositorio.findById(sujetoDelCliente)
                .map(this::resolverFusion)
                .orElse(null);

        if (encontrado == null) {
            /*
             * Un identificador que no esta en la base se acepta tal cual.
             *
             * Viene de una cookie legitima que no llego a persistirse, y
             * rechazarlo obligaria al cliente a cambiar de identificador y a
             * perder su propio rastro. Un UUID v4 no se adivina.
             */
            return repositorio.save(Sujeto.builder()
                    .id(sujetoDelCliente)
                    .creadoEn(Instant.now())
                    .vistoEn(Instant.now())
                    .build());
        }

        if (encontrado.getUsuarioId() != null) {
            /*
             * Resuelve a un sujeto CON cuenta, pero aqui no hay sesion iniciada:
             * se estrena uno anonimo NUEVO, con identificador propio.
             *
             * Pasa al cerrar sesion —el navegador conserva el ultimo
             * identificador, que ya era el de la cuenta— y en un equipo
             * compartido. Sin este corte, el siguiente visitante seguiria
             * alimentando el perfil de quien se fue y veria SUS
             * recomendaciones. Tambien cierra el caso de quien copia un
             * identificador ajeno y lo manda sin autenticarse.
             *
             * El identificador NO se reutiliza: chocaria con la fila existente.
             */
            return repositorio.save(nuevo(null));
        }

        return encontrado;
    }

    /** Sigue la cadena de fusiones hasta el sujeto que sobrevivió. */
    private Sujeto resolverFusion(Sujeto sujeto) {
        Sujeto actual = sujeto;
        for (int salto = 0; actual.estaFusionado() && salto < 5; salto++) {
            Optional<Sujeto> destino = repositorio.findById(actual.getFusionadoEn());
            if (destino.isEmpty()) {
                break;
            }
            actual = destino.get();
        }
        return actual;
    }

    /**
     * Traslada el rastro del anónimo a la cuenta.
     *
     * <p>Los eventos y las impresiones se reasignan; las facetas se SUMAN, no
     * se mueven, porque las dos partes pueden tener la misma y la clave
     * primaria lo impediría. Lo que ya traía la cuenta se decae hasta ahora
     * antes de sumarle lo del anónimo, o un perfil dormido pesaría como si
     * fuera de hoy.
     *
     * <p>La fila del anónimo NO se borra: queda como redirección porque puede
     * haber pestañas abiertas mandando todavía ese identificador.
     */
    private void fusionar(Sujeto anonimo, Sujeto cuenta) {
        long vidaMediaSeg = pesos.getVidaMedia().toSeconds();

        repositorio.moverEventos(anonimo.getId(), cuenta.getId());
        repositorio.moverImpresiones(anonimo.getId(), cuenta.getId());
        repositorio.fundirPerfil(anonimo.getId(), cuenta.getId(), vidaMediaSeg);
        repositorio.fundirDescartes(anonimo.getId(), cuenta.getId());
        repositorio.borrarPerfil(anonimo.getId());

        anonimo.setFusionadoEn(cuenta.getId());
        repositorio.save(anonimo);

        log.info("Sujeto anonimo {} fusionado en la cuenta {}", anonimo.getId(), cuenta.getId());
    }

    private Sujeto nuevo(Long usuarioId) {
        Instant ahora = Instant.now();
        return Sujeto.builder()
                .id(UUID.randomUUID())
                .usuarioId(usuarioId)
                .creadoEn(ahora)
                .vistoEn(ahora)
                .build();
    }
}
