import 'dart:io' show Platform;

/// De dónde saca la app los datos.
///
/// Es la misma API que consume la web: no hay backend aparte ni datos
/// duplicados. Lo que cambia es cómo se llega a él.
///
/// EL PUNTO QUE SIEMPRE FALLA: `localhost` desde el móvil no es el ordenador,
/// es el propio teléfono. Por eso no se puede reutilizar la URL de la web tal
/// cual y hay un valor distinto según dónde corra la app:
///
///   - Emulador de Android → 10.0.2.2, que es el alias del anfitrión.
///   - Simulador de iOS     → localhost, porque comparte red con el Mac.
///   - Móvil de verdad      → la IP del ordenador en la wifi (192.168.x.x).
///
/// Para un dispositivo físico o para producción se pasa al arrancar:
///
///   flutter run --dart-define=API_URL=http://192.168.1.40:8080
///
/// Se apunta al gateway (:8080) y no a cada servicio: es la única puerta de
/// entrada, la que enruta a catálogo, usuarios y compras.
class Entorno {
  const Entorno._();

  /// Valor inyectado en tiempo de compilación; gana sobre los de por defecto.
  static const String _apiUrlDefinida = String.fromEnvironment('API_URL');

  static String get apiUrl {
    if (_apiUrlDefinida.isNotEmpty) return _apiUrlDefinida;
    if (Platform.isAndroid) return 'http://10.0.2.2:8080';
    return 'http://localhost:8080';
  }

  static String get apiBase => '$apiUrl/api';

  /// Cuánto se espera a una respuesta antes de darla por perdida. En móvil la
  /// red es peor que en escritorio, así que se es más paciente que en la web.
  static const Duration tiempoEspera = Duration(seconds: 20);

  /// true cuando la app apunta a un backend local, para poder avisar en
  /// pantalla si no hay conexión en vez de dejar un error mudo.
  static bool get esEntornoLocal =>
      apiUrl.contains('localhost') || apiUrl.contains('10.0.2.2') || apiUrl.contains('192.168.');
}
