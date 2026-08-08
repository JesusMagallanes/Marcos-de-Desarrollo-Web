package com.backend.catalogo.chatbot;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.CategoriaService;
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

    /** Palabra clave → slug de categoría. */
    private static final Map<String, String> CATEGORIAS = Map.ofEntries(
            Map.entry("laptop", "laptops"),
            Map.entry("monitor", "monitores"),
            Map.entry("celular", "celulares"),
            Map.entry("smartphone", "celulares"),
            Map.entry("consola", "consolas"),
            Map.entry("accesorio", "accesorios"),
            Map.entry("teclado", "teclados"),
            Map.entry("mouse", "mouse"),
            Map.entry("auricular", "auriculares"),
            Map.entry("tablet", "tablets"));

    public RespuestaChat responder(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return new RespuestaChat("Por favor, escribe un mensaje.", "error", List.of(), null);
        }

        String texto = mensaje.toLowerCase().trim();

        if (contiene(texto, "oferta", "descuento", "promocion", "promo")) {
            return ofertas();
        }
        if (contiene(texto, "envío", "envio", "delivery", "entrega")) {
            return informativa(TEXTO_ENVIO, "envio");
        }
        if (contiene(texto, "pago", "pagar", "tarjeta", "yape", "plin", "transferencia")) {
            return informativa(TEXTO_PAGO, "pago");
        }
        if (contiene(texto, "contacto", "telefono", "teléfono", "whatsapp", "email", "correo")) {
            return informativa(TEXTO_CONTACTO, "contacto");
        }

        String slug = CATEGORIAS.entrySet().stream()
                .filter(e -> texto.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        return slug != null ? porCategoria(slug) : buscar(texto);
    }

    private RespuestaChat ofertas() {
        List<ProductoResponse> productos = productoService.listar(null).stream().limit(5).toList();

        if (productos.isEmpty()) {
            return new RespuestaChat("No hay ofertas disponibles en este momento.", "ofertas", List.of(), null);
        }

        StringBuilder sb = new StringBuilder("🔥 <b>Ofertas destacadas:</b><br><br>");
        productos.forEach(p -> sb.append(linea(p)));
        sb.append("👉 Toca un producto para ver el detalle.");

        return new RespuestaChat(sb.toString(), "ofertas", productos, null);
    }

    private RespuestaChat porCategoria(String slug) {
        // Se pregunta si existe en vez de capturar la excepción: una excepción
        // lanzada aquí dentro marcaría la transacción como rollback-only y el
        // commit fallaría aunque la capturásemos.
        if (!categoriaService.existePorSlug(slug)) {
            return new RespuestaChat("Todavía no tengo productos de esa categoría.", "categoria", List.of(), null);
        }

        List<ProductoResponse> productos = productoService.listarPorCategoria(slug, 0, 5).content();

        if (productos.isEmpty()) {
            return new RespuestaChat("No hay productos disponibles en esa categoría ahora mismo.",
                    "categoria", List.of(), slug);
        }

        StringBuilder sb = new StringBuilder("💻 <b>Esto es lo que tengo:</b><br><br>");
        productos.forEach(p -> sb.append(linea(p)));

        return new RespuestaChat(sb.toString(), "categoria", productos, slug);
    }

    private RespuestaChat buscar(String consulta) {
        List<ProductoResponse> resultados = productoService.listar(consulta).stream().limit(5).toList();

        if (resultados.isEmpty()) {
            return new RespuestaChat(
                    "🤔 No encontré productos con esa búsqueda.<br><br>"
                            + "Prueba con: <b>laptops</b>, <b>monitores</b>, <b>celulares</b>, "
                            + "<b>ofertas</b>, <b>envíos</b>, <b>pagos</b> o <b>contacto</b>.",
                    "busqueda", List.of(), null);
        }

        StringBuilder sb = new StringBuilder(
                String.format("🔍 <b>Encontré %d producto(s):</b><br><br>", resultados.size()));
        resultados.forEach(p -> sb.append(linea(p)));

        return new RespuestaChat(sb.toString(), "busqueda", resultados, null);
    }

    private RespuestaChat informativa(String html, String tipo) {
        return new RespuestaChat(html, tipo, List.of(), null);
    }

    private String linea(ProductoResponse p) {
        return String.format(
                "🔹 <b>%s</b><br>💲 <b>S/ %.2f</b><br>%s<br><br>",
                escapar(p.name()),
                p.precio(),
                p.stock() > 0 ? "✅ Disponible" : "❌ Sin stock");
    }

    /**
     * El nombre del producto se inserta en HTML, así que se escapa: un nombre
     * con etiquetas no debe convertirse en markup en el navegador.
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

    private boolean contiene(String texto, String... claves) {
        for (String clave : claves) {
            if (texto.contains(clave)) {
                return true;
            }
        }
        return false;
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
