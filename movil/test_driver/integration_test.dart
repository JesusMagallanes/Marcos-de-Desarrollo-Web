import 'dart:io';

import 'package:integration_test/integration_test_driver_extended.dart';

/// Driver de `flutter drive`: corre en el PC y guarda cada captura que pide
/// la prueba que corre en el teléfono.
Future<void> main() => integrationDriver(
  onScreenshot: (nombre, bytes, [args]) async {
    final carpeta = Platform.environment['CAPTURAS'] ?? 'build/capturas';
    final archivo = File('$carpeta/$nombre.png');
    await archivo.create(recursive: true);
    await archivo.writeAsBytes(bytes);
    return true;
  },
);
