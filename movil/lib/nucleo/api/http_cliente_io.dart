import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/entorno.dart';

/// Cliente para Android/iOS/desktop: usa `HttpClient` de `dart:io` para poder
/// fijar el tiempo de conexión, que el cliente por defecto no expone.
http.Client crearClienteHttp() {
  final base = HttpClient()..connectionTimeout = Entorno.tiempoEspera;
  return _IOClient(base);
}

class _IOClient extends http.BaseClient {
  _IOClient(this._interno);
  final HttpClient _interno;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest peticion) async {
    final salida = await _interno.openUrl(peticion.method, peticion.url);
    peticion.headers.forEach(salida.headers.set);

    final cuerpo = await peticion.finalize().toBytes();
    if (cuerpo.isNotEmpty) salida.add(cuerpo);

    final entrada = await salida.close();

    // `HttpHeaders` no expone las claves como colección, solo permite
    // recorrerlas, así que se copian a un mapa a mano.
    final cabeceras = <String, String>{};
    entrada.headers.forEach((nombre, valores) {
      cabeceras[nombre] = valores.join(',');
    });

    return http.StreamedResponse(
      entrada,
      entrada.statusCode,
      contentLength: entrada.contentLength == -1 ? null : entrada.contentLength,
      headers: cabeceras,
      reasonPhrase: entrada.reasonPhrase,
    );
  }

  @override
  void close() => _interno.close();
}
