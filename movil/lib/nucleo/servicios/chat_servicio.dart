// lib/nucleo/servicios/chat_servicio.dart
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../config/entorno.dart';
import '../modelos/mensaje_chat.dart';

class ChatServicio {
  // La misma URL que el resto de la app: en el emulador de Android
  // `localhost` es el propio teléfono, y Entorno ya lo resuelve a 10.0.2.2.
  String get apiUrl => Entorno.apiUrl;

  Future<MensajeChat> enviarMensaje(String texto) async {
    try {
      final response = await http
          .post(
            Uri.parse('$apiUrl/api/chatbot/mensaje'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'mensaje': texto, 'historial': []}),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);

        String respuesta = data['respuesta'] ?? "No entendí eso.";
        ProductoRecomendado? producto;

        if (data['productos'] != null &&
            (data['productos'] as List).isNotEmpty) {
          final p = data['productos'][0];

          String? imgUrl = p['imageUrl'];
          if (imgUrl != null && imgUrl.startsWith('/')) {
            imgUrl = '$apiUrl$imgUrl';
          }

          producto = ProductoRecomendado(
            id: p['id'] ?? 0,
            categoria: p['categoriaName'] ?? 'LAPTOPS',
            nombre: p['name'] ?? 'Producto',
            descripcion: p['description'] ?? '',
            precio: (p['precioActual'] ?? p['precio'] ?? 0).toDouble(),
            precioAnterior: p['precioOferta'] != null
                ? (p['precio'] as num).toDouble()
                : null,
            rating: (p['calificacionPromedio'] ?? 0).toDouble(),
            reviews: p['cantidadValoraciones'] ?? 0,
            imagenUrl: imgUrl,
          );
        }

        return MensajeChat(
          texto: respuesta,
          esUsuario: false,
          producto: producto,
        );
      } else {
        debugPrint(' Error del backend (${response.statusCode}): ${response.body}');
        return MensajeChat(
          texto: "El servidor respondió con un error (${response.statusCode}).",
          esUsuario: false,
        );
      }
    } catch (e) {
      debugPrint(' Error de conexión en ChatServicio: $e');
      return MensajeChat(
        texto: "Hubo un error de conexión. Revisa la consola.",
        esUsuario: false,
      );
    }
  }
}
