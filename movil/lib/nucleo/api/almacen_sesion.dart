import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Dónde viven los tokens en el móvil.
///
/// Se usa almacenamiento seguro (Keystore en Android, Keychain en iOS) y no
/// SharedPreferences. La web guarda el token en localStorage porque en un
/// navegador no hay mucho más, pero en móvil sí lo hay: SharedPreferences es
/// un XML en claro dentro del sandbox de la app, legible en cuanto el
/// dispositivo esté rooteado o se saque una copia de seguridad.
///
/// El token de refresco es el que más importa proteger: dura mucho más que el
/// de acceso, así que quien lo robe se queda dentro.
class AlmacenSesion {
  /// `encryptedSharedPreferences` usa EncryptedSharedPreferences de AndroidX
  /// en lugar del modo clásico: cifra también las CLAVES, no solo los valores.
  ///
  /// En iOS se fija `first_unlock` porque el valor por defecto deja el dato
  /// accesible incluso con el dispositivo bloqueado.
  static const _almacen = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  static const _claveAcceso = 'token_acceso';
  static const _claveRefresco = 'token_refresco';
  static const _claveRol = 'rol';
  static const _claveEmail = 'email';

  Future<String?> get tokenAcceso => _almacen.read(key: _claveAcceso);
  Future<String?> get tokenRefresco => _almacen.read(key: _claveRefresco);
  Future<String?> get rol => _almacen.read(key: _claveRol);
  Future<String?> get email => _almacen.read(key: _claveEmail);

  Future<void> guardar({
    required String tokenAcceso,
    required String tokenRefresco,
    required String rol,
    required String email,
  }) async {
    await Future.wait([
      _almacen.write(key: _claveAcceso, value: tokenAcceso),
      _almacen.write(key: _claveRefresco, value: tokenRefresco),
      _almacen.write(key: _claveRol, value: rol),
      _almacen.write(key: _claveEmail, value: email),
    ]);
  }

  /// Tras refrescar solo cambia el token de acceso; el resto sigue valiendo.
  Future<void> actualizarAcceso(String token) =>
      _almacen.write(key: _claveAcceso, value: token);

  Future<void> limpiar() async {
    await Future.wait([
      _almacen.delete(key: _claveAcceso),
      _almacen.delete(key: _claveRefresco),
      _almacen.delete(key: _claveRol),
      _almacen.delete(key: _claveEmail),
    ]);
  }

  /// Hay sesión guardada de una vez anterior. Es lo que permite que la app
  /// abra ya con la sesión iniciada si la cuenta sigue vigente.
  Future<bool> get haySesion async => (await tokenRefresco)?.isNotEmpty ?? false;
}
