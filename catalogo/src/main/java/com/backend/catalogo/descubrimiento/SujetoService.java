package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.shared.seguridad.LimitadorPeticiones;

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
    private final FirmaSujeto firmas;
    private final LimitadorPeticiones limitador;
    private final MetricasSeguridad seguridad;
    private final PesosDescubrimiento pesos;

    /**
     * El sujeto de esta petición.
     *
     * <h4>El identificador hay que poseerlo, no conocerlo</h4>
     *
     * <p>Lo que dice el navegador solo se acepta si viene con la firma que lo
     * acompaña. Sin ella —o con una que no cuadre— la petición estrena sujeto,
     * exactamente igual que quien llega por primera vez.
     *
     * <p>No se devuelve un error, y es deliberado. Un 400 distinguiría «esa
     * firma no vale» de «ese sujeto no existe», y eso convierte el endpoint en
     * un oráculo para averiguar qué identificadores están vivos. Además el caso
     * corriente de una firma ausente no es un ataque: es alguien que limpió el
     * navegador, y para esa persona lo correcto es empezar de cero sin fricción.
     *
     * @param usuarioId del JWT; {@code null} si nadie inició sesión
     * @param sujetoDelCliente el que dice el navegador
     * @param firma la que acompaña a ese identificador
     */
    @Transactional
    public Sujeto resolver(Long usuarioId, UUID sujetoDelCliente, String firma, String ubigeo,
            String procedencia) {
        UUID reclamado = firmas.valida(sujetoDelCliente, firma) ? sujetoDelCliente : null;

        Resuelto resuelto = usuarioId != null
                ? new Resuelto(deUsuario(usuarioId, reclamado), true)
                : anonimo(reclamado, procedencia);

        Sujeto sujeto = resuelto.sujeto();
        sujeto.setVistoEn(Instant.now());
        if (ubigeo != null && !ubigeo.isBlank()) {
            sujeto.setUbigeo(ubigeo);
        }
        /*
         * El guardado es condicional, y esa condicion es la que sostiene el
         * freno a fabricar identidades. Aqui se llamaba a `save` siempre, asi
         * que el sujeto efimero que devuelve `estrenar` al pasarse de cupo
         * acababa persistido de todas formas y el limite no limitaba nada.
         * Lo encontro la prueba que contaba cuantas filas nacian.
         */
        return resuelto.persistir() ? repositorio.save(sujeto) : sujeto;
    }

    /**
     * Un sujeto y si debe quedar guardado.
     *
     * <p>Existe por el caso efímero: cuando una procedencia se pasa del cupo de
     * identidades nuevas, la petición se sirve con normalidad —quien navega
     * detrás de una IP compartida no tiene culpa— pero no deja fila.
     */
    private record Resuelto(Sujeto sujeto, boolean persistir) {
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
         *
         * El identificador llega ya comprobado desde `resolver`, y esa
         * comprobación importa MAS aqui que en el camino anonimo: sin ella,
         * quien tuviera una cuenta y conociera el identificador de un visitante
         * cualquiera podia absorber su rastro entero —sus intereses, su
         * conducta— dentro de la suya con una sola peticion.
         */
        if (sujetoDelCliente != null && !sujetoDelCliente.equals(cuenta.getId())) {
            repositorio.findById(sujetoDelCliente)
                    .filter(s -> !s.estaFusionado())
                    .filter(s -> s.getUsuarioId() == null)
                    .ifPresent(anonimo -> fusionar(anonimo, cuenta));
        }
        return cuenta;
    }

    private Resuelto anonimo(UUID sujetoDelCliente, String procedencia) {
        if (sujetoDelCliente == null) {
            return estrenar(procedencia);
        }

        Sujeto encontrado = repositorio.findById(sujetoDelCliente)
                .map(this::resolverFusion)
                .orElse(null);

        if (encontrado == null) {
            /*
             * Firmado por nosotros pero sin fila: se acepta y se crea.
             *
             * Pasa cuando la fila se purgo o la escritura no llego a
             * persistirse. Ya no es una via de creacion libre: para llegar
             * hasta aqui el identificador tuvo que venir con una firma valida,
             * y esa firma solo la emite el servidor al estrenar un sujeto.
             */
            return new Resuelto(Sujeto.builder()
                    .id(sujetoDelCliente)
                    .creadoEn(Instant.now())
                    .vistoEn(Instant.now())
                    .build(), true);
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
            return estrenar(procedencia);
        }

        return new Resuelto(encontrado, true);
    }

    /**
     * Estrena un sujeto anónimo, con freno.
     *
     * <h4>Por qué el cupo está aquí</h4>
     *
     * <p>Limitar la ingesta por sujeto no sirve de nada si fabricar sujetos es
     * gratis: quien quiera más cupo se inventa más identidades y multiplica el
     * suyo. Este es el único sitio donde nacen, así que es el único sitio donde
     * se puede cerrar esa amplificación.
     *
     * <p>Y funciona porque desde H el identificador va firmado: ya no se puede
     * inventar uno por fuera y usarlo, hay que pedirlo. Pedirlo pasa por aquí.
     *
     * <h4>Qué hace al pasarse</h4>
     *
     * <p>Devuelve un sujeto EFÍMERO, con identificador propio y sin guardar.
     * La petición se sirve con normalidad —quien navega detrás de una IP
     * compartida no tiene culpa de nada y no puede ver la tienda rota— pero no
     * deja fila ni acumula rastro, que es lo que la creación en masa buscaba.
     */
    private Resuelto estrenar(String procedencia) {
        String clave = (procedencia == null ? "desconocida" : procedencia) + "|sujetos-nuevos";

        if (!limitador.permitir(clave, pesos.getSujetosNuevosPorIpPorMinuto(),
                Duration.ofMinutes(1))) {
            seguridad.rateLimitBloqueado("sujetos-nuevos");
            return new Resuelto(nuevo(null), false);
        }
        return new Resuelto(nuevo(null), true);
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

        /*
         * Sin identificadores. Se registraban los dos UUID, y desde H eso es
         * peor que ruido: el identificador va acompanado de una firma y el par
         * es una credencial, pero el identificador solo ya bastaba antes para
         * suplantar. Un log es lo mas facil de exportar sin querer —a un
         * agregador, a una captura, a un ticket— y no hay ninguna pregunta
         * operativa que necesite saber QUIEN se fusiono. Que ocurrio, si.
         */
        log.info("Fusionado un sujeto anonimo en una cuenta");
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
