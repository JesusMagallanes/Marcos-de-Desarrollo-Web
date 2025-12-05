package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/chatbot")
public class ChatBotController {

    private final ProductoService productoService;
    private final CategoriaService categoriaService;

    public ChatBotController(ProductoService productoService, CategoriaService categoriaService) {
        this.productoService = productoService;
        this.categoriaService = categoriaService;
    }

    @PostMapping("/mensaje")
    public Map<String, Object> procesarMensaje(@RequestBody Map<String, String> request) {
        String mensaje = request.get("mensaje");
        log.info("Mensaje del chatbot recibido: {}", mensaje);
        
        Map<String, Object> response = new HashMap<>();
        
        if (mensaje == null || mensaje.trim().isEmpty()) {
            response.put("respuesta", "Por favor, escribe un mensaje.");
            response.put("tipo", "error");
            return response;
        }
        
        String mensajeLower = mensaje.toLowerCase().trim();
        
        // Procesar según el tipo de consulta
        if (esConsultaOfertas(mensajeLower)) {
            response = obtenerOfertas();
        } else if (esConsultaEnvio(mensajeLower)) {
            response = obtenerInfoEnvio();
        } else if (esConsultaPago(mensajeLower)) {
            response = obtenerInfoPago();
        } else if (esConsultaContacto(mensajeLower)) {
            response = obtenerInfoContacto();
        } else if (esConsultaCategoria(mensajeLower)) {
            response = obtenerProductosPorCategoria(mensajeLower);
        } else {
            response = buscarProductos(mensajeLower);
        }
        
        return response;
    }

    private boolean esConsultaOfertas(String mensaje) {
        return mensaje.contains("oferta") || mensaje.contains("descuento") || 
               mensaje.contains("promocion") || mensaje.contains("promo");
    }

    private boolean esConsultaEnvio(String mensaje) {
        return mensaje.contains("envío") || mensaje.contains("envio") || 
               mensaje.contains("delivery") || mensaje.contains("entrega");
    }

    private boolean esConsultaPago(String mensaje) {
        return mensaje.contains("pago") || mensaje.contains("pagar") || 
               mensaje.contains("tarjeta") || mensaje.contains("yape") || 
               mensaje.contains("plin") || mensaje.contains("transferencia");
    }

    private boolean esConsultaContacto(String mensaje) {
        return mensaje.contains("contacto") || mensaje.contains("telefono") || 
               mensaje.contains("teléfono") || mensaje.contains("whatsapp") || 
               mensaje.contains("email") || mensaje.contains("correo");
    }

    private boolean esConsultaCategoria(String mensaje) {
        return mensaje.contains("laptop") || mensaje.contains("monitor") || 
               mensaje.contains("celular") || mensaje.contains("smartphone") || 
               mensaje.contains("consola") || mensaje.contains("accesorio") ||
               mensaje.contains("teclado") || mensaje.contains("mouse") ||
               mensaje.contains("auricular") || mensaje.contains("tablet");
    }

    private Map<String, Object> obtenerOfertas() {
        Map<String, Object> response = new HashMap<>();
        List<ProductosResponseDTO> productos = productoService.listarProducto();
        
        // Filtrar productos en oferta (puedes agregar un campo "enOferta" en el modelo)
        List<ProductosResponseDTO> ofertas = productos.stream()
            .limit(5)
            .collect(Collectors.toList());
        
        StringBuilder mensaje = new StringBuilder("🔥 <b>Ofertas Destacadas:</b><br><br>");
        
        if (ofertas.isEmpty()) {
            mensaje.append("No hay ofertas disponibles en este momento.");
        } else {
            for (ProductosResponseDTO producto : ofertas) {
                mensaje.append(String.format(
                    "🔹 <b>%s</b><br>💲 Precio: <b>S/ %.2f</b><br>",
                    producto.getName(),
                    producto.getPrecio()
                ));
                if (producto.getStock() > 0) {
                    mensaje.append("✅ Stock disponible<br>");
                } else {
                    mensaje.append("❌ Sin stock<br>");
                }
                mensaje.append("<br>");
            }
            mensaje.append("👉 Haz clic en un producto para ver más detalles.");
        }
        
        response.put("respuesta", mensaje.toString());
        response.put("tipo", "ofertas");
        response.put("productos", ofertas);
        return response;
    }

    private Map<String, Object> obtenerInfoEnvio() {
        Map<String, Object> response = new HashMap<>();
        String mensaje = "🚚 <b>Información de Envíos:</b><br><br>" +
                        "📦 Envíos gratis en compras mayores a <b>S/ 200</b><br>" +
                        "⏱️ Tiempo de entrega: <b>1-3 días hábiles</b><br>" +
                        "🌍 Cobertura: Lima Metropolitana y provincias<br>" +
                        "📍 Puedes rastrear tu pedido en tiempo real<br><br>" +
                        "💡 <i>Tip: Agrega más productos para calificar a envío gratis</i>";
        
        response.put("respuesta", mensaje);
        response.put("tipo", "envio");
        return response;
    }

    private Map<String, Object> obtenerInfoPago() {
        Map<String, Object> response = new HashMap<>();
        String mensaje = "💳 <b>Métodos de Pago Aceptados:</b><br><br>" +
                        "💳 Tarjetas de crédito/débito (Visa, Mastercard, Amex)<br>" +
                        "📱 Yape y Plin<br>" +
                        "🏦 Transferencias bancarias<br>" +
                        "💰 Pago contra entrega (Lima Metropolitana)<br>" +
                        "🔒 Todos los pagos son 100% seguros<br><br>" +
                        "🎁 <b>Hasta 12 cuotas sin intereses</b> con tarjetas participantes";
        
        response.put("respuesta", mensaje);
        response.put("tipo", "pago");
        return response;
    }

    private Map<String, Object> obtenerInfoContacto() {
        Map<String, Object> response = new HashMap<>();
        String mensaje = "📞 <b>Contáctanos:</b><br><br>" +
                        "📱 <b>Teléfono:</b> <a href='tel:+51987654321'>+51 987 654 321</a><br>" +
                        "💬 <b>WhatsApp:</b> <a href='https://wa.me/51987654321' target='_blank'>Chatear ahora</a><br>" +
                        "📧 <b>Email:</b> <a href='mailto:soporte@smartzone.com'>soporte@smartzone.com</a><br>" +
                        "🌐 <b>Web:</b> <a href='https://smartzone.com' target='_blank'>smartzone.com</a><br><br>" +
                        "⏰ <b>Horario de atención:</b><br>" +
                        "Lunes a Viernes: 9:00 AM - 6:00 PM<br>" +
                        "Sábados: 9:00 AM - 1:00 PM";
        
        response.put("respuesta", mensaje);
        response.put("tipo", "contacto");
        return response;
    }

    private Map<String, Object> obtenerProductosPorCategoria(String mensaje) {
        Map<String, Object> response = new HashMap<>();
        
        // Determinar la categoría solicitada
        String categoriaSlug = null;
        if (mensaje.contains("laptop")) {
            categoriaSlug = "laptops";
        } else if (mensaje.contains("monitor")) {
            categoriaSlug = "monitores";
        } else if (mensaje.contains("celular") || mensaje.contains("smartphone")) {
            categoriaSlug = "celulares";
        } else if (mensaje.contains("consola")) {
            categoriaSlug = "consolas";
        } else if (mensaje.contains("accesorio")) {
            categoriaSlug = "accesorios";
        } else if (mensaje.contains("teclado")) {
            categoriaSlug = "teclados";
        } else if (mensaje.contains("mouse")) {
            categoriaSlug = "mouse";
        } else if (mensaje.contains("auricular")) {
            categoriaSlug = "auriculares";
        } else if (mensaje.contains("tablet")) {
            categoriaSlug = "tablets";
        }
        
        if (categoriaSlug == null) {
            return buscarProductos(mensaje);
        }
        
        CategoriaModel categoria = categoriaService.obtenerPorSlug(categoriaSlug);
        
        if (categoria == null) {
            response.put("respuesta", "No encontré esa categoría. ¿Puedes ser más específico?");
            response.put("tipo", "error");
            return response;
        }
        
        List<ProductosResponseDTO> productos = productoService.listarProducto().stream()
            .filter(p -> p.getCategoriaNombre() != null && 
                        p.getCategoriaNombre().equalsIgnoreCase(categoria.getName()))
            .limit(5)
            .collect(Collectors.toList());
        
        StringBuilder mensajeBuilder = new StringBuilder();
        mensajeBuilder.append(String.format("💻 <b>Top %s:</b><br><br>", categoria.getName()));
        
        if (productos.isEmpty()) {
            mensajeBuilder.append("No hay productos disponibles en esta categoría en este momento.");
        } else {
            for (ProductosResponseDTO producto : productos) {
                mensajeBuilder.append(String.format(
                    "🔹 <b>%s</b><br>",
                    producto.getName()
                ));
                
                if (producto.getDescription() != null && !producto.getDescription().isEmpty()) {
                    String descripcionCorta = producto.getDescription().length() > 100 
                        ? producto.getDescription().substring(0, 100) + "..." 
                        : producto.getDescription();
                    mensajeBuilder.append(descripcionCorta).append("<br>");
                }
                
                mensajeBuilder.append(String.format("💲 Precio: <b>S/ %.2f</b><br>", producto.getPrecio()));
                
                if (producto.getStock() > 0) {
                    mensajeBuilder.append("✅ Stock disponible<br>");
                } else {
                    mensajeBuilder.append("❌ Sin stock<br>");
                }
                
                mensajeBuilder.append("<br>");
            }
            mensajeBuilder.append(String.format(
                "👉 <a href='/productos/categoria/%s'>Ver todos los %s</a>",
                categoria.getSlug(),
                categoria.getName()
            ));
        }
        
        response.put("respuesta", mensajeBuilder.toString());
        response.put("tipo", "categoria");
        response.put("categoria", categoria.getName());
        response.put("productos", productos);
        return response;
    }

    private Map<String, Object> buscarProductos(String query) {
        Map<String, Object> response = new HashMap<>();
        List<ProductosResponseDTO> productos = productoService.listarProducto();
        
        // Buscar productos que coincidan con la consulta
        List<ProductosResponseDTO> resultados = productos.stream()
            .filter(p -> 
                (p.getName() != null && p.getName().toLowerCase().contains(query)) ||
                (p.getDescription() != null && p.getDescription().toLowerCase().contains(query)) ||
                (p.getCategoriaNombre() != null && p.getCategoriaNombre().toLowerCase().contains(query))
            )
            .limit(5)
            .collect(Collectors.toList());
        
        StringBuilder mensaje = new StringBuilder();
        
        if (resultados.isEmpty()) {
            mensaje.append("🤔 No encontré productos que coincidan con tu búsqueda.<br><br>");
            mensaje.append("Intenta con: <b>laptops</b>, <b>monitores</b>, <b>celulares</b>, ");
            mensaje.append("<b>consolas</b>, <b>ofertas</b>, <b>envíos</b>, <b>pagos</b> o <b>contacto</b>.");
        } else {
            mensaje.append(String.format("🔍 <b>Encontré %d producto(s):</b><br><br>", resultados.size()));
            
            for (ProductosResponseDTO producto : resultados) {
                mensaje.append(String.format(
                    "🔹 <b>%s</b><br>💲 Precio: <b>S/ %.2f</b><br>",
                    producto.getName(),
                    producto.getPrecio()
                ));
                
                if (producto.getStock() > 0) {
                    mensaje.append("✅ Stock disponible<br>");
                } else {
                    mensaje.append("❌ Sin stock<br>");
                }
                
                mensaje.append("<br>");
            }
            
            mensaje.append("👉 Haz clic en un producto para ver más detalles.");
        }
        
        response.put("respuesta", mensaje.toString());
        response.put("tipo", "busqueda");
        response.put("productos", resultados);
        return response;
    }

    @GetMapping("/categorias")
    public List<Map<String, String>> obtenerCategorias() {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        return categorias.stream()
            .map(cat -> {
                Map<String, String> catMap = new HashMap<>();
                catMap.put("nombre", cat.getName());
                catMap.put("slug", cat.getSlug());
                return catMap;
            })
            .collect(Collectors.toList());
    }
}
