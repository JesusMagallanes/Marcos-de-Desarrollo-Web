import 'package:http/http.dart' as http;

/// Cliente para el navegador: usa el `http.Client` por defecto, que en web
/// se apoya en las peticiones del propio navegador (no en `dart:io`, que ahí
/// no está disponible). No permite fijar el tiempo de conexión, pero el
/// `.timeout()` que ya se aplica sobre el envío cumple la misma función.
http.Client crearClienteHttp() => http.Client();
