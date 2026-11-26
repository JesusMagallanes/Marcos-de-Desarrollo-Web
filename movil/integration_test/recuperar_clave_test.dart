import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:smartzone_movil/main.dart' as app;

/// Recuperar la contraseña desde la app, contra el backend real.
///
/// Va en dos fases porque el código llega por correo y el teléfono no lo ve:
///
///   1. `--dart-define=FASE=pedir`: pide el código desde la pantalla.
///   2. Se lee el código del correo (sin SMTP, del log de `usuarios`).
///   3. `--dart-define=FASE=cambiar --dart-define=CODIGO=123456
///       --dart-define=CLAVE_NUEVA=...`: entra por «Ya tengo un código», se
///      equivoca una vez, acierta y entra con la contraseña nueva.
///
/// Cambia de verdad la contraseña de [_email]: usar contra una base de pruebas.
const _email = String.fromEnvironment(
  'EMAIL',
  defaultValue: 'admin@smartzone.com',
);
const _fase = String.fromEnvironment('FASE', defaultValue: 'pedir');
const _codigo = String.fromEnvironment('CODIGO');
const _claveNueva = String.fromEnvironment('CLAVE_NUEVA');

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  var superficieConvertida = false;
  Future<void> captura(WidgetTester t, String nombre) async {
    await t.pump(const Duration(milliseconds: 300));
    if (!superficieConvertida && Platform.isAndroid) {
      await binding.convertFlutterSurfaceToImage();
      superficieConvertida = true;
      await t.pump();
    }
    await binding.takeScreenshot(nombre);
    debugPrint('CAPTURA $nombre');
  }

  Future<void> esperar(WidgetTester t, Finder f, {int segundos = 30}) async {
    final fin = DateTime.now().add(Duration(seconds: segundos));
    while (DateTime.now().isBefore(fin)) {
      await t.pump(const Duration(milliseconds: 200));
      if (f.evaluate().isNotEmpty) return;
    }
    final textos = find
        .byType(Text)
        .evaluate()
        .map((e) => (e.widget as Text).data)
        .whereType<String>()
        .take(40)
        .join(' | ');
    debugPrint('EN PANTALLA: $textos');
    await captura(t, 'zz_fallo_$_fase');
    throw TestFailure('No apareció: $f');
  }

  Future<void> tocar(WidgetTester t, Finder f) async {
    FocusManager.instance.primaryFocus?.unfocus();
    await t.pump(const Duration(milliseconds: 600));
    await esperar(t, f);
    await t.ensureVisible(f.first);
    await t.pump(const Duration(milliseconds: 300));
    await t.tap(f.first, warnIfMissed: false);
    await t.pump(const Duration(milliseconds: 500));
  }

  /// Desde donde arranque la app, hasta la pantalla de recuperar.
  Future<void> abrirRecuperar(WidgetTester t) async {
    app.main();
    await esperar(
      t,
      find.byWidgetPredicate(
        (w) => w is Text && (w.data == 'Omitir' || w.data == 'Inicio'),
      ),
    );
    if (find.text('Omitir').evaluate().isNotEmpty) {
      await tocar(t, find.text('Omitir'));
    }
    await esperar(t, find.text('Inicio'));
    if (find.text('Perfil').evaluate().isNotEmpty) {
      await tocar(t, find.text('Perfil'));
      await tocar(t, find.text('Cerrar sesión'));
    }
    await tocar(t, find.text('Entrar'));
    await tocar(t, find.text('Iniciar sesión'));
    await tocar(t, find.text('¿Olvidaste tu clave?'));
    await esperar(t, find.text('Enviar código'));
    await t.enterText(find.byType(TextFormField).first, _email);
  }

  testWidgets('recuperar la contraseña: fase $_fase', (t) async {
    await abrirRecuperar(t);

    if (_fase == 'pedir') {
      await captura(t, 'r1_pedir');
      await tocar(t, find.text('Enviar código'));
      await esperar(t, find.text('Revisa tu correo'));
      expect(find.textContaining('Si hay una cuenta con $_email'), findsOneWidget);
      await captura(t, 'r2_codigo_pedido');
      return;
    }

    expect(_codigo, matches(RegExp(r'^\d{6}$')), reason: 'falta CODIGO');
    expect(_claveNueva, isNotEmpty, reason: 'falta CLAVE_NUEVA');

    await tocar(t, find.text('Ya tengo un código'));
    await esperar(t, find.text('Cambiar contraseña'));
    final campos = find.byType(TextFormField);

    // Primero un código equivocado: debe decirlo y no cambiar nada.
    final equivocado = _codigo == '000000' ? '111111' : '000000';
    await t.enterText(campos.at(0), equivocado);
    await t.enterText(campos.at(1), _claveNueva);
    await t.enterText(campos.at(2), _claveNueva);
    await tocar(t, find.text('Cambiar contraseña'));
    await esperar(t, find.textContaining('El código no es válido'));
    await captura(t, 'r3_codigo_equivocado');

    // Ahora el bueno.
    await t.enterText(campos.at(0), _codigo);
    await tocar(t, find.text('Cambiar contraseña'));
    await esperar(t, find.textContaining('Contraseña cambiada'));
    await esperar(t, find.text('Ingresar a SmartZone'));
    await captura(t, 'r4_vuelta_al_login');

    // El login quedó con el correo escrito: solo falta la clave nueva.
    final login = find.byType(TextFormField);
    expect(
      t.widget<TextFormField>(login.at(0)).controller?.text,
      _email,
    );
    await t.enterText(login.at(1), _claveNueva);
    await tocar(t, find.text('Ingresar a SmartZone'));
    await esperar(t, find.text('Perfil'));
    await captura(t, 'r5_dentro_con_clave_nueva');
  });
}
