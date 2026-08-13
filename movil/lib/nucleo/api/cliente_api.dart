import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/entorno.dart';
import 'almacen_sesion.dart';
import 'error_api.dart';

/// Cliente HTTP contra el gateway. Es el equivalente de los interceptores de
/// la web (auth, error, correlación) reunidos en un sitio.
///
/// Hace tres cosas que no conviene repetir en cada pantalla:
///
///  1. Adjunta el token de acceso.
///  2. Si la respuesta es 401, refresca la sesión UNA vez y reintenta. Si el
///     refresco también falla, cierra sesión y avisa.
///  3. Traduce los errores del backend (RFC 7807) a ErrorApi.
class ClienteApi {
  ClienteApi({AlmacenSesion? almacen, http.Client? http})
      : _almacen = almacen ?? AlmacenSesion(),
        _http = http ?? _crearCliente();

  final AlmacenSesion _almacen;
  final http.Client _http;

  /// Se avisa hacia arriba cuando la sesión muere para que la app lleve al
  /// login sin que cada pantalla tenga que comprobarlo.
  void Function()? alCerrarSesion;

  /// Refresco en curso. Si tres peticiones reciben 401 a la vez, solo una
  /// canjea el token de refresco y las otras dos esperan a ese mismo intento;
  /// si no, se pisarían entre ellas y el backend revocaría el token bueno.
  Future<bool>? _refrescoEnCurso;

  static http.Client _crearCliente() {
    final base = HttpClient()..connectionTimeout = Entorno.tiempoEspera;
    return IOClient(base);
  }

  Future<dynamic> get(String ruta, {Map<String, String>? consulta}) =>
      _enviar('GET', ruta, consulta: consulta);

  Future<dynamic> post(String ruta, {Object? cuerpo}) =>
      _enviar('POST', ruta, cuerpo: cuerpo);

  Future<dynamic> put(String ruta, {Object? cuerpo}) =>
      _enviar('PUT', ruta, cuerpo: cuerpo);

  Future<dynamic> patch(String ruta, {Object? cuerpo}) =>
      _enviar('PATCH', ruta, cuerpo: cuerpo);

  Future<dynamic> delete(String ruta) => _enviar('DELETE', ruta);

  Future<dynamic> _enviar(
    String metodo,
    String ruta, {
    Object? cuerpo,
    Map<String, String>? consulta,
    bool reintentado = false,
  }) async {
    final uri = Uri.parse('${Entorno.apiBase}$ruta').replace(
      queryParameters: consulta?.isEmpty ?? true ? null : consulta,
    );

    final cabeceras = <String, String>{
      'Accept': 'application/json',
      if (cuerpo != null) 'Content-Type': 'application/json',
    };

    final token = await _almacen.tokenAcceso;
    if (token != null && token.isNotEmpty) {
      cabeceras['Authorization'] = 'Bearer $token';
    }

    http.Response respuesta;
    try {
      final peticion = http.Request(metodo, uri)..headers.addAll(cabeceras);
      if (cuerpo != null) peticion.body = jsonEncode(cuerpo);

      final flujo = await _http.send(peticion).timeout(Entorno.tiempoEspera);
      respuesta = await http.Response.fromStream(flujo);
    } on SocketException catch (e) {
      throw ErrorApi.sinConexion(_pistaDeRed(e));
    } on TimeoutException {
      throw ErrorApi.sinConexion('La respuesta tardó demasiado.');
    } on HttpException catch (e) {
      throw ErrorApi.sinConexion(e.message);
    }

    // Sesión caducada: se intenta refrescar una sola vez.
    if (respuesta.statusCode == 401 && !reintentado && !_esRutaDeAuth(ruta)) {
      final renovada = await _refrescar();
      if (renovada) {
        return _enviar(metodo, ruta,
            cuerpo: cuerpo, consulta: consulta, reintentado: true);
      }
      await _almacen.limpiar();
      alCerrarSesion?.call();
    }

    return _interpretar(respuesta);
  }

  /// El login y el registro devuelven 401 por credenciales malas, no por
  /// sesión caducada: refrescar ahí no tiene sentido y además borraría la
  /// sesión de quien solo se equivocó de contraseña.
  bool _esRutaDeAuth(String ruta) =>
      ruta.startsWith('/auth/login') ||
      ruta.startsWith('/auth/registrar') ||
      ruta.startsWith('/auth/refresh');

  Future<bool> _refrescar() {
    // Si ya hay uno en marcha, se espera a ese en vez de lanzar otro.
    return _refrescoEnCurso ??= _hacerRefresco().whenComplete(() {
      _refrescoEnCurso = null;
    });
  }

  Future<bool> _hacerRefresco() async {
    final refresco = await _almacen.tokenRefresco;
    if (refresco == null || refresco.isEmpty) return false;

    try {
      final respuesta = await _http
          .post(
            Uri.parse('${Entorno.apiBase}/auth/refresh'),
            headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
            body: jsonEncode({'refreshToken': refresco}),
          )
          .timeout(Entorno.tiempoEspera);

      if (respuesta.statusCode != 200) return false;

      final datos = jsonDecode(respuesta.body) as Map<String, dynamic>;
      final nuevo = datos['accessToken'] as String?;
      if (nuevo == null || nuevo.isEmpty) return false;

      await _almacen.actualizarAcceso(nuevo);
      return true;
    } catch (_) {
      return false;
    }
  }

  dynamic _interpretar(http.Response respuesta) {
    final estado = respuesta.statusCode;

    if (estado == 204 || respuesta.body.isEmpty) {
      if (estado >= 400) {
        throw ErrorApi(estado: estado, titulo: 'Error', mensaje: 'El servidor respondió $estado.');
      }
      return null;
    }

    dynamic cuerpo;
    try {
      cuerpo = jsonDecode(utf8.decode(respuesta.bodyBytes));
    } catch (_) {
      if (estado >= 400) {
        throw ErrorApi(estado: estado, titulo: 'Error', mensaje: 'Respuesta no válida del servidor.');
      }
      return null;
    }

    if (estado >= 400) {
      throw cuerpo is Map<String, dynamic>
          ? ErrorApi.desdeJson(estado, cuerpo)
          : ErrorApi(estado: estado, titulo: 'Error', mensaje: 'El servidor respondió $estado.');
    }
    return cuerpo;
  }

  /// Un "connection refused" contra una IP local casi siempre es lo mismo: el
  /// backend no está levantado, o el móvil no puede llegar al ordenador. Vale
  /// la pena decirlo en vez de soltar el error de socket en crudo.
  String _pistaDeRed(SocketException e) {
    if (!Entorno.esEntornoLocal) return 'Revisa tu conexión a internet.';
    return 'Comprueba que el backend esté levantado y que ${Entorno.apiUrl} '
        'sea alcanzable desde este dispositivo.';
  }

  void cerrar() => _http.close();
}

/// `IOClient` sobre un `HttpClient` propio: es lo que permite fijar el tiempo
/// de conexión, que el cliente por defecto no expone.
class IOClient extends http.BaseClient {
  IOClient(this._interno);
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
