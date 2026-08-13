import 'package:flutter/foundation.dart';

import '../api/cliente_api.dart';
import '../api/error_api.dart';
import '../modelos/carrito.dart';

/// Carrito del usuario. Exige sesión: el backend lo asocia al `uid` del token
/// y nunca acepta un id de usuario por parámetro.
///
/// Es un ChangeNotifier porque el contador de la barra inferior y la pantalla
/// del carrito tienen que ver siempre lo mismo.
class CarritoServicio extends ChangeNotifier {
  CarritoServicio(this._api);
  final ClienteApi _api;

  Carrito _carrito = const Carrito.vacio();
  bool _cargando = false;
  ErrorApi? _error;

  Carrito get carrito => _carrito;
  bool get cargando => _cargando;
  ErrorApi? get error => _error;
  int get unidades => _carrito.unidades;

  /// Cada operación devuelve el carrito completo, así que no hay que volver a
  /// pedirlo después de cambiar algo.
  Future<void> cargar() => _ejecutar(() => _api.get('/carrito'));

  Future<void> agregar(int productoId, int cantidad) =>
      _ejecutar(() => _api.post('/carrito/items',
          cuerpo: {'productoId': productoId, 'cantidad': cantidad}));

  Future<void> cambiarCantidad(int itemId, int cantidad) =>
      _ejecutar(() => _api.put('/carrito/items/$itemId', cuerpo: {'cantidad': cantidad}));

  Future<void> quitar(int itemId) => _ejecutar(() => _api.delete('/carrito/items/$itemId'));

  Future<void> vaciar() => _ejecutar(() => _api.delete('/carrito'));

  /// Al cerrar sesión el carrito de memoria debe irse: si no, el siguiente en
  /// entrar en el mismo móvil vería las líneas del anterior hasta el primer
  /// refresco.
  void olvidar() {
    _carrito = const Carrito.vacio();
    _error = null;
    notifyListeners();
  }

  Future<void> _ejecutar(Future<dynamic> Function() operacion) async {
    _cargando = true;
    _error = null;
    notifyListeners();

    try {
      final datos = await operacion();
      if (datos is Map<String, dynamic>) _carrito = Carrito.desdeJson(datos);
    } on ErrorApi catch (e) {
      _error = e;
    } finally {
      _cargando = false;
      notifyListeners();
    }
  }
}
