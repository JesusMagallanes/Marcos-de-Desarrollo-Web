import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../servicios/sesion.dart';

class FavoritosServicio extends ChangeNotifier {
  FavoritosServicio(this._preferencias, this._sesion) {
    _usuarioId = _sesion.usuario?.id;
    _sesion.addListener(_alCambiarSesion);
  }

  static const String _prefijo = 'favoritos_usuario_';

  final SharedPreferences _preferencias;
  final Sesion _sesion;

  int? _usuarioId;
  Set<int> _ids = <int>{};
  bool _cargando = false;
  String? _error;

  Set<int> get ids => Set<int>.unmodifiable(_ids);
  int get cantidad => _ids.length;
  bool get cargando => _cargando;
  String? get error => _error;

  Future<void> iniciar() => _recargar();

  Future<void> recargar() => _recargar();

  bool esFavorito(int productoId) =>
      _sesion.autenticado && _ids.contains(productoId);

  Future<bool> alternar(int productoId) async {
    if (!_sesion.autenticado || _usuarioId == null) return false;

    final anteriores = Set<int>.from(_ids);
    if (_ids.contains(productoId)) {
      _ids.remove(productoId);
    } else {
      _ids.add(productoId);
    }
    _error = null;
    notifyListeners();

    try {
      await _guardar();
      return true;
    } catch (_) {
      _ids = anteriores;
      _error = 'No pudimos guardar tus favoritos. Inténtalo de nuevo.';
      notifyListeners();
      return false;
    }
  }

  Future<void> _guardar() {
    final clave = '$_prefijo$_usuarioId';
    return _preferencias.setStringList(
      clave,
      _ids.map((id) => id.toString()).toList(),
    );
  }

  Future<void> _recargar() async {
    final id = _usuarioId;
    if (id == null) {
      _ids = <int>{};
      _cargando = false;
      notifyListeners();
      return;
    }

    _cargando = true;
    notifyListeners();
    try {
      final datos =
          _preferencias.getStringList('$_prefijo$id') ?? const <String>[];
      _ids = datos.map(int.tryParse).whereType<int>().toSet();
      _error = null;
    } catch (_) {
      _ids = <int>{};
      _error = 'No pudimos cargar tus favoritos. Inténtalo de nuevo.';
    }
    _cargando = false;
    notifyListeners();
  }

  void _alCambiarSesion() {
    final id = _sesion.usuario?.id;
    if (id == _usuarioId) return;

    _usuarioId = id;
    _ids = <int>{};
    _cargando = id != null;
    _error = null;
    notifyListeners();
    if (id != null) _recargar();
  }

  @override
  void dispose() {
    _sesion.removeListener(_alCambiarSesion);
    super.dispose();
  }
}
