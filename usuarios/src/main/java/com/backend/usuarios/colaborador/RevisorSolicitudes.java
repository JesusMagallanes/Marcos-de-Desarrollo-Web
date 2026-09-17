package com.backend.usuarios.colaborador;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.backend.usuarios.colaborador.dto.SolicitudDtos.AnalisisSolicitud;
import com.backend.usuarios.colaborador.dto.SolicitudDtos.NegocioParaRevision;
import com.backend.usuarios.shared.ia.ClienteIa;
import com.backend.usuarios.shared.ia.GuardiaPrompt;

import tools.jackson.databind.JsonNode;

/**
 * Pre-revisión de una solicitud para vender, antes de que el administrador la mire.
 *
 * <p>La verificación de identidad la hace una persona comparando el documento
 * con las fotos, y eso no cambia. Lo que añade la IA es otra cosa: leer el
 * negocio que se describe y señalar si encaja en una tienda de tecnología, si el
 * rubro y la descripción se contradicen, o si el texto suena a reventa de
 * réplicas. Es lo que un administrador con cincuenta solicitudes en la bandeja
 * lee en diagonal.
 *
 * <p>Al modelo solo le llegan nombre comercial, rubro y descripción: ver
 * {@link NegocioParaRevision}. Nada de documento, titular, teléfono ni domicilio.
 */
@Service
public class RevisorSolicitudes {

    static final String FUNCION = "revision_solicitud";

    private final ClienteIa ia;
    private final GuardiaPrompt guardia;
    private final SolicitudService solicitudes;

    public RevisorSolicitudes(ClienteIa ia, GuardiaPrompt guardia, SolicitudService solicitudes) {
        this.ia = ia;
        this.guardia = guardia;
        this.solicitudes = solicitudes;
    }

    public AnalisisSolicitud analizar(Long id) {
        NegocioParaRevision n = solicitudes.negocioParaRevision(id);
        GuardiaPrompt.Entrada nombre = guardia.limpiar(n.nombreComercial(), 120);
        GuardiaPrompt.Entrada rubro = guardia.limpiar(n.rubro(), 120);
        GuardiaPrompt.Entrada descripcion = guardia.limpiar(n.descripcion(), 1000);

        List<String> motivos = new ArrayList<>();
        if (nombre.sospechosa() || rubro.sospechosa() || descripcion.sospechosa()) {
            motivos.add("El texto contiene instrucciones dirigidas a un revisor automático.");
            return new AnalisisSolicitud("ALTO", motivos, "Rechazar o pedir que reescriba la solicitud.", false);
        }
        String riesgoReglas = "BAJO";
        if (descripcion.texto().length() < 60) {
            motivos.add("Descripción muy breve: dice poco del negocio.");
            riesgoReglas = "MEDIO";
        }

        String sistema = """
                Ayudas a revisar solicitudes de personas y empresas que quieren vender en \
                SmartZone, una tienda de tecnología de Perú. Un administrador decide; tú señalas \
                lo que merece su atención. La identidad ya la verifica una persona con documentos: \
                tú solo evalúas el NEGOCIO descrito.

                ALTO: vende cosas ajenas a la tecnología o prohibidas, o sugiere réplicas, \
                productos "genéricos iguales al original", mercancía sin procedencia.
                MEDIO: rubro y descripción no encajan entre sí, descripción vaga o copiada, \
                promesas poco creíbles.
                BAJO: negocio de tecnología coherente y descrito con claridad.
                %s

                Responde SOLO JSON: {"riesgo": "BAJO|MEDIO|ALTO", \
                "motivos": ["máximo 4, frases cortas"], "sugerencia": "una frase para el administrador"}
                """.formatted(GuardiaPrompt.REGLA_DATOS);
        String usuario = "Tipo: " + (n.tipoPersona() == TipoPersona.JURIDICA ? "empresa" : "persona natural")
                + "\n\n" + guardia.delimitar("nombre_comercial", nombre.texto())
                + "\n\n" + guardia.delimitar("rubro", rubro.texto())
                + "\n\n" + guardia.delimitar("descripcion", descripcion.texto());

        String base = riesgoReglas;
        return ia.json(FUNCION, sistema, usuario, 300)
                .map(r -> combinar(base, motivos, r))
                .orElseGet(() -> new AnalisisSolicitud(base, motivos,
                        "Revisar documentos y descripción del negocio.", false));
    }

    private static AnalisisSolicitud combinar(String riesgoReglas, List<String> motivosReglas, JsonNode r) {
        String riesgoIa = r.path("riesgo").asString("").trim().toUpperCase(Locale.ROOT);
        if (!List.of("BAJO", "MEDIO", "ALTO").contains(riesgoIa)) {
            riesgoIa = "MEDIO";
        }
        LinkedHashSet<String> motivos = new LinkedHashSet<>(motivosReglas);
        for (JsonNode m : r.path("motivos")) {
            if (motivos.size() >= 5) {
                break;
            }
            if (m.isString() && !m.asString().isBlank()) {
                String t = m.asString().trim();
                motivos.add(t.length() > 180 ? t.substring(0, 180) + "…" : t);
            }
        }
        String sugerencia = r.path("sugerencia").isString() ? r.path("sugerencia").asString().trim() : "";
        if (sugerencia.length() > 220) {
            sugerencia = sugerencia.substring(0, 220) + "…";
        }
        return new AnalisisSolicitud(
                nivel(riesgoIa) >= nivel(riesgoReglas) ? riesgoIa : riesgoReglas,
                List.copyOf(motivos),
                sugerencia.isEmpty() ? "Revisar documentos y descripción del negocio." : sugerencia,
                true);
    }

    private static int nivel(String riesgo) {
        return List.of("BAJO", "MEDIO", "ALTO").indexOf(riesgo);
    }
}
