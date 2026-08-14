package com.backend.catalogo.chatbot;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.chatbot.dto.ChatbotDtos.RespuestaChat;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatbotService {

    private final ProductoService productoService;
    private final CategoriaService categoriaService;

    /** Acentos y diacríticos fuera: "envío" y "envio" son la misma intención. */
    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

    public RespuestaChat responder(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return new RespuestaChat("Por favor, escribe un mensaje.", "error", List.of(), null);
        }

        String texto = normalizar(mensaje);

        if (contiene(texto, "oferta", "descuento", "promocion", "promo")) {
            return ofertas();
        }
        if (contiene(texto, "envio", "entrega", "delivery", "enviar")) {
            return informativa(TEXTO_ENVIO, "envio");
        }
        if (contiene(texto, "pago", "pagar", "tarjeta", "yape", "plin", "transferencia", "cuota", "cuotas")) {
            return informativa(TEXTO_PAGO, "pago");
        }
        if (contiene(texto, "contacto", "telefono", "whatsapp", "email", "correo", "atencion")) {
            return informativa(TEXTO_CONTACTO, "contacto");
        }

        // La categoría va antes que el saludo: "hola, laptop" debe responder la
        // categoría, y "hola" a secas cae al saludo.
        Optional<CategoriaResponse> categoria = detectarCategoria(texto);
        if (categoria.isPresent()) {
            return porCategoria(categoria.get());
        }

        if (contiene(texto, "hola", "buenos dias", "buenas tardes", "buenas noches", "buenas", "saludos", "hello")) {
            return saludo();
        }
        if (contiene(texto, "gracias", "thank", "genial", "perfecto", "excelente")) {
            return gracias();
        }
        if (contiene(texto, "ayuda", "help", "que puedes", "puedes hacer", "opciones", "menu")) {
            return ayuda();
        }

        return buscar(mensaje.trim());
    }

    /**
     * Ofertas reales: solo productos cuyo descuento está vigente hoy (los
     * demás se descartan antes de mostrarlos).
     */
    private RespuestaChat ofertas() {
        List<ProductoResponse> productos = productoService.listar(null).stream()
                .filter(ProductoResponse::enOferta)
                .limit(5)
                .toList();

        if (productos.isEmpty()) {
            return new RespuestaChat(
                    "🔥 <b>Ahora mismo no hay productos con descuento.</b><br>"
                            + "Vuelve a preguntarme por <b>ofertas</b> en otro momento o dime "
                            + "una categoría para mostrarte lo que tengo.",
                    "ofertas", List.of(), null);
        }

        int cantidad = productos.size();
        return new RespuestaChat(
                String.format("🔥 <b>Hay %d producto%s con descuento:</b><br><br>"
                        + "👉 Toca una tarjeta para ver el detalle.",
                        cantidad, cantidad == 1 ? "" : "s"),
                "ofertas", productos, null);
    }

    private RespuestaChat porCategoria(CategoriaResponse categoria) {
        List<ProductoResponse> productos = productoService
                .listarPorCategoria(categoria.slug(), 0, 5).content();

        if (productos.isEmpty()) {
            return new RespuestaChat(
                    "No tengo productos disponibles en <b>" + escapar(categoria.name()) + "</b> ahora mismo.",
                    "categoria", List.of(), categoria.slug());
        }

        return new RespuestaChat(
                "💻 <b>Esto es lo que tengo en " + escapar(categoria.name()) + ":</b><br><br>"
                        + "👉 Toca una tarjeta para ver el detalle.",
                "categoria", productos, categoria.slug());
    }

    private RespuestaChat buscar(String consulta) {
        List<ProductoResponse> resultados = productoService.listar(consulta).stream().limit(5).toList();

        if (resultados.isEmpty()) {
            return new RespuestaChat(
                    "🤔 No encontré productos con «" + escapar(consulta) + "».<br><br>"
                            + "Prueba con una <b>categoría</b> (laptops, monitores, celulares…), "
                            + "o con <b>ofertas</b>, <b>envíos</b>, <b>pagos</b>, <b>contacto</b> o <b>ayuda</b>.",
                    "busqueda", List.of(), null);
        }

        int cantidad = resultados.size();
        return new RespuestaChat(
                String.format("🔍 <b>Encontré %d producto%s</b> para «%s»:<br><br>"
                        + "👉 Toca una tarjeta para ver el detalle.",
                        cantidad, cantidad == 1 ? "" : "s", escapar(consulta)),
                "busqueda", resultados, null);
    }

    private RespuestaChat saludo() {
        return new RespuestaChat(
                "¡Hola! 👋 Soy el asistente de <b>SmartZone</b>.<br><br>"
                        + "Puedo ayudarte con <b>ofertas</b>, <b>categorías</b> "
                        + "(laptops, monitores, celulares…), <b>envíos</b>, <b>pagos</b> y <b>contacto</b>.<br>"
                        + "¿En qué te ayudo?",
                "saludo", List.of(), null);
    }

    private RespuestaChat gracias() {
        return new RespuestaChat(
                "¡De nada! 😊 Para eso estoy.<br>"
                        + "¿Hay algo más en lo que pueda ayudarte?",
                "gracias", List.of(), null);
    }

    private RespuestaChat ayuda() {
        return new RespuestaChat(
                "🤖 <b>Esto es lo que sé hacer:</b><br><br>"
                        + "🔥 <b>Ofertas</b> → te muestro los productos con descuento vigente.<br>"
                        + "📁 <b>Una categoría</b> → laptops, monitores, celulares…<br>"
                        + "🔎 <b>Buscar productos</b> → por nombre o descripción.<br>"
                        + "🚚 <b>Envíos</b> y 💳 <b>Métodos de pago</b>.<br>"
                        + "📞 <b>Contacto</b> y horario de atención.<br><br>"
                        + "Prueba escribiendo algo como «ofertas» o «quiero una laptop».",
                "ayuda", List.of(), null);
    }

    private RespuestaChat informativa(String html, String tipo) {
        return new RespuestaChat(html, tipo, List.of(), null);
    }

    /**
     * Busca la categoría del catálogo que mejor encaje con el mensaje, mirando
     * los nombres reales (no un mapa fijo): "laptop" y "laptops" dan en el
     * clavo, igual que "monitor" y "monitores". Devuelve la coincidencia más
     * larga para no quedarse con una parcial.
     */
    private Optional<CategoriaResponse> detectarCategoria(String texto) {
        return categoriaService.listar().stream()
                .map(categoria -> new Coincidencia(categoria, coincidencia(categoria, texto)))
                .filter(c -> c.clave() != null)
                .max(Comparator.comparingInt(c -> c.clave().length()))
                .map(Coincidencia::categoria);
    }

    private record Coincidencia(CategoriaResponse categoria, String clave) {
    }

    /** Palabra clave de la categoría que aparece en el mensaje, o null. */
    private String coincidencia(CategoriaResponse categoria, String texto) {
        String nombre = normalizar(categoria.name());
        if (nombre.length() >= 4 && texto.length() >= 4
                && (texto.contains(nombre) || nombre.contains(texto))) {
            return nombre;
        }

        for (String token : nombre.split("[^a-z0-9]+")) {
            if (token.length() < 4 || token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if (texto.contains(token) || texto.contains(singular(token))) {
                return token;
            }
        }
        return null;
    }

    /** Singular aproximado: "monitores" → "monitor", "laptops" → "laptop". */
    private String singular(String palabra) {
        if (palabra.endsWith("es") && palabra.length() > 4) {
            return palabra.substring(0, palabra.length() - 2);
        }
        if (palabra.endsWith("s") && palabra.length() > 3) {
            return palabra.substring(0, palabra.length() - 1);
        }
        return palabra;
    }

    /** Minúsculas y sin acentos, para comparar sin fricción de tipeo. */
    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD);
        sinAcentos = DIACRITICOS.matcher(sinAcentos).replaceAll("");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private boolean contiene(String texto, String... claves) {
        for (String clave : claves) {
            if (texto.contains(clave)) {
                return true;
            }
        }
        return false;
    }

    /**
     * El texto del usuario (o el nombre de una categoría) se inserta en HTML,
     * así que se escapa: un nombre con etiquetas no debe convertirse en markup.
     */
    private String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String TEXTO_ENVIO = """
            🚚 <b>Información de envíos:</b><br><br>
            📦 Envío gratis en compras mayores a <b>S/ 200</b><br>
            ⏱️ Entrega en <b>1 a 3 días hábiles</b><br>
            🌍 Cobertura: Lima Metropolitana y provincias<br>
            📍 Puedes seguir tu pedido desde "Mis compras"
            """;

    private static final String TEXTO_PAGO = """
            💳 <b>Métodos de pago:</b><br><br>
            💳 Visa, Mastercard y American Express<br>
            📱 Yape y Plin<br>
            🏦 Transferencia bancaria<br>
            💰 Pago contra entrega (Lima Metropolitana)<br><br>
            🎁 Hasta <b>12 cuotas sin intereses</b> con tarjetas participantes
            """;

    private static final String TEXTO_CONTACTO = """
            📞 <b>Contáctanos:</b><br><br>
            📱 Teléfono: <b>+51 987 654 321</b><br>
            💬 WhatsApp: <b>+51 987 654 321</b><br>
            📧 Correo: <b>soporte@smartzone.com</b><br><br>
            ⏰ Lunes a viernes de 9:00 a 18:00, sábados de 9:00 a 13:00
            """;
}
