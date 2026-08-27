import 'package:flutter/foundation.dart';

import '../api/almacen_sesion.dart';
import '../api/cliente_api.dart';
import '../api/error_api.dart';
import '../modelos/usuario.dart';

/// Estado de la sesión, compartido por toda la app.
///
/// Es lo que resuelve el "si el usuario ya tiene cuenta vigente, que entre":
/// al abrir, [restaurar] comprueba si quedó un token de refresco de una vez
/// anterior y, si sigue siendo válido, recupera la sesión sin pedir nada. Si
/// caducó o fue revocado, se limpia y se muestra el login.
class Sesion extends ChangeNotifier {
  Sesion(this._api, {AlmacenSesion? almacen}) : _almacen = almacen ?? AlmacenSesion() {
    // Si el cliente HTTP da la sesión por perdida (el refresco falló), la app
    // entera debe enterarse, no solo la pantalla que hizo la petición.
    _api.alCerrarSesion = () {
      _usuario = null;
      notifyListeners();
    };
  }

  final ClienteApi _api;
  final AlmacenSesion _almacen;

  Usuario? _usuario;
  bool _cargando = true;

  Usuario? get usuario => _usuario;
  bool get autenticado => _usuario != null;
  /// true mientras se comprueba si había sesión previa; evita el parpadeo de
  /// enseñar el login durante un instante a quien ya estaba dentro.
  bool get cargando => _cargando;

  /// Se llama una vez al arrancar la app.
  Future<void> restaurar() async {
    _cargando = true;
    notifyListeners();

    try {
      if (await _almacen.haySesion) {
        // `/auth/yo` devuelve el perfil del usuario del token, que es el mismo
        // endpoint que usa la web. Con pedirlo basta para validar la sesión: si
        // el token de acceso caducó, el cliente lo refresca solo; si el de
        // refresco ya no vale (caducado o revocado al cerrar sesión en otro
        // sitio), responde 401 y se cae al catch.
        final datos = await _api.get('/auth/yo');
        _usuario = Usuario.desdeJson(datos as Map<String, dynamic>);
      }
    } catch (_) {
      await _almacen.limpiar();
      _usuario = null;
    } finally {
      _cargando = false;
      notifyListeners();
    }
  }

  /// Inicia sesión con una cuenta existente. Devuelve null si todo fue bien,
  /// o el error para que la pantalla lo enseñe.
  Future<ErrorApi?> entrar(String email, String password) async {
    try {
      final datos = await _api.post('/auth/login', cuerpo: {
        'email': email.trim(),
        'password': password,
      }) as Map<String, dynamic>;

      await _almacen.guardar(
        tokenAcceso: datos['accessToken'] as String,
        tokenRefresco: datos['refreshToken'] as String,
        rol: (datos['rol'] ?? 'CLIENTE').toString(),
        email: email.trim(),
      );

      _usuario = Usuario.desdeJson(datos['usuario'] as Map<String, dynamic>);
      notifyListeners();
      return null;
    } on ErrorApi catch (e) {
      return e;
    }
  }

  Future<ErrorApi?> registrar({
    required String nombre,
    required String apellidos,
    required String email,
    required String password,
    required String telefono,
    required String direccion,
  }) async {
    try {
      await _api.post('/auth/registrar', cuerpo: {
        'name': nombre.trim(),
        'lastname': apellidos.trim(),
        'emailAddress': email.trim(),
        'password': password,
        'phoneNumber': telefono.trim(),
        'address': direccion.trim(),
      });
      // El registro no devuelve sesión: se entra a continuación con los mismos
      // datos, que es lo que hace la web.
      //
      // El `await` es necesario, no decorativo: sin él se devuelve el Future y
      // el `catch` de abajo queda fuera de su alcance, así que un ErrorApi al
      // entrar se propagaba como excepción en vez de volver como valor. Quien
      // llama espera un ErrorApi de retorno y no lo captura.
      return await entrar(email, password);
    } on ErrorApi catch (e) {
      return e;
    }
  }

  Future<void> salir() async {
    final refresco = await _almacen.tokenRefresco;
    try {
      // Avisar al backend permite revocar el token de refresco; si falla, se
      // cierra igual en local, que es lo que el usuario espera al pulsar.
      if (refresco != null && refresco.isNotEmpty) {
        await _api.post('/auth/logout', cuerpo: {'refreshToken': refresco});
      }
    } catch (_) {
      // Sin conexión no se puede revocar; se limpia igualmente.
    }
    await _almacen.limpiar();
    _usuario = null;
    notifyListeners();
  }
}
