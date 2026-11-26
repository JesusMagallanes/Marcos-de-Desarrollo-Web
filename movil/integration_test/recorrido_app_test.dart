import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:smartzone_movil/main.dart' as app;
import 'package:smartzone_movil/nucleo/servicios/carrito_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/catalogo_servicio.dart';
import 'package:provider/provider.dart';

/// Recorrido de la app real contra el backend real (gateway en :8080).
///
///   flutter drive -d emulator-5554 --driver=test_driver/integration_test.dart ///     --target=integration_test/recorrido_app_test.dart
///
/// Las capturas las guarda el driver en la carpeta de CAPTURAS (variable de
/// entorno del PC) o en build/capturas.
///
/// Usa la misma cuenta que test/integracion_backend_test.dart.
const _email = 'admin@smartzone.com';
const _clave = 'SmartZone2026!';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  var superficieConvertida = false;
  Future<void> captura(WidgetTester t, String nombre) async {
    await t.pump(const Duration(milliseconds: 300));
    // En Android hay que pasar la superficie a imagen una vez antes de la
    // primera captura; el driver del PC (test_driver/) recibe cada PNG.
    if (!superficieConvertida && Platform.isAndroid) {
      await binding.convertFlutterSurfaceToImage();
      superficieConvertida = true;
      await t.pump();
    }
    await binding.takeScreenshot(nombre);
    debugPrint('CAPTURA $nombre');
  }

  /// Bombea hasta que aparezca [f] (la red va en tiempo real).
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
    await captura(t, 'zz_fallo');
    throw TestFailure('No apareció: $f');
  }

  Future<void> tocar(WidgetTester t, Finder f) async {
    // Con el teclado abierto el botón queda debajo y el toque cae en el
    // teclado; una persona lo cerraría antes.
    FocusManager.instance.primaryFocus?.unfocus();
    await t.pump(const Duration(milliseconds: 600));
    await esperar(t, f);
    await t.ensureVisible(f.first);
    await t.pump(const Duration(milliseconds: 300));
    await t.tap(f.first, warnIfMissed: false);
    await t.pump(const Duration(milliseconds: 500));
  }

  int unidadesCarrito(WidgetTester t) {
    final ctx = t.element(find.byType(MaterialApp).first);
    return Provider.of<CarritoServicio>(ctx, listen: false).carrito.unidades;
  }

  testWidgets('recorrido completo de la app', (t) async {
    app.main();
    await esperar(
      t,
      find.byWidgetPredicate(
        (w) => w is Text && (w.data == 'Omitir' || w.data == 'Inicio'),
      ),
    );

    // 1. Onboarding (solo la primera vez en esta máquina).
    if (find.text('Omitir').evaluate().isNotEmpty) {
      await captura(t, '00_bienvenida');
      await tocar(t, find.text('Omitir'));
    }

    // 2. Tienda.
    await esperar(t, find.text('Inicio'));
    await esperar(t, find.textContaining('S/ '));
    await captura(t, '01_inicio');

    // 3. Login (si la sesión quedó abierta de antes, se salta).
    if (find.text('Entrar').evaluate().isNotEmpty) {
      await tocar(t, find.text('Entrar'));
      await tocar(t, find.text('Iniciar sesión'));
      await esperar(t, find.text('Ingresar a SmartZone'));
      await captura(t, '02_login');

      // 3a. Recuperar clave: abre en el paso de pedir el código. No se pide
      // ninguno: cambiaría la contraseña de una cuenta real (el flujo entero
      // está en recuperar_clave_test.dart, para una base de pruebas).
      await tocar(t, find.text('¿Olvidaste tu clave?'));
      await esperar(t, find.text('Enviar código'));
      expect(find.text('Ya tengo un código'), findsOneWidget);
      await captura(t, '03_recuperar_clave');
      await t.binding.handlePopRoute();
      await t.pump(const Duration(seconds: 1));

      // 3b. Entrar.
      await esperar(t, find.text('Ingresar a SmartZone'));
      final campos = find.byType(TextFormField);
      await t.enterText(campos.at(0), _email);
      await t.enterText(campos.at(1), _clave);
      await tocar(t, find.text('Ingresar a SmartZone'));
    }
    await esperar(t, find.text('Perfil'));
    await tocar(t, find.text('Inicio'));
    await esperar(t, find.textContaining('S/ '));
    await captura(t, '04_inicio_con_sesion');

    // 4. Chat con el asesor IA desde el globo de la cabecera del inicio.
    await tocar(t, find.byTooltip('Hablar con el Asesor IA'));
    await esperar(t, find.byIcon(Icons.send));
    await t.enterText(find.byType(TextField).last, 'laptop para jugar');
    await tocar(t, find.byIcon(Icons.send));
    // Respuesta del servidor: aparece una tarjeta o un mensaje de error.
    await esperar(
      t,
      find.byWidgetPredicate(
        (w) =>
            w is Text &&
            (w.data == 'Ver detalle' ||
                (w.data ?? '').contains('error') ||
                (w.data ?? '').contains('No entendí')),
      ),
      segundos: 45,
    );
    await captura(t, '05_chat');

    if (find.text('Ver detalle').evaluate().isNotEmpty) {
      expect(find.textContaining('<b>'), findsNothing);
      expect(find.textContaining('<br>'), findsNothing);
      final antes = unidadesCarrito(t);
      if (find.text('Sin stock').evaluate().isNotEmpty) {
        // Recomendó algo agotado: el botón debe estar deshabilitado.
        final boton = t.widget<ElevatedButton>(
          find.widgetWithText(ElevatedButton, 'Sin stock'),
        );
        expect(boton.onPressed, isNull);
        await captura(t, '06_chat_sin_stock');
        debugPrint('CARRITO desde chat: producto agotado, botón deshabilitado');
      } else {
        await t.pump(const Duration(seconds: 2));
        await tocar(t, find.widgetWithText(ElevatedButton, 'Agregar'));
        await esperar(t, find.text('Ver carrito'));
        await captura(t, '06_chat_agregar');
        final despues = unidadesCarrito(t);
        debugPrint('CARRITO desde chat: antes=$antes despues=$despues');
        expect(despues, greaterThan(antes));
      }
    }
    await t.binding.handlePopRoute();
    await t.pump(const Duration(seconds: 1));

    // 5. Producto desde la tienda y agregar al carrito.
    await tocar(t, find.text('Inicio'));
    // El catálogo real tiene productos agotados: se elige el primero con
    // stock, según lo que cargó la propia app.
    final catalogo = Provider.of<CatalogoServicio>(
      t.element(find.byType(MaterialApp).first),
      listen: false,
    );
    final conStock = (catalogo.productosEnCache ?? const [])
        .where((p) => !p.sinStock)
        .first;
    debugPrint('PRODUCTO con stock: ${conStock.nombre} (${conStock.stock})');
    final tarjeta = find
        .ancestor(
          of: find.text(conStock.nombre).first,
          matching: find.byType(GestureDetector),
        )
        .first;
    // La cuenta y el carrito son reales y compartidos: se anota cuántas
    // unidades había de este producto y se dejan igual al terminar.
    final servicio = Provider.of<CarritoServicio>(
      t.element(find.byType(MaterialApp).first),
      listen: false,
    );
    int cantidadDe(int productoId) => servicio.carrito.items
        .where((l) => l.productoId == productoId)
        .fold(0, (n, l) => n + l.cantidad);
    final cantidadInicial = cantidadDe(conStock.id);
    try {
      final antesMas = unidadesCarrito(t);
      await tocar(t, find.descendant(of: tarjeta, matching: find.byIcon(Icons.add)));
      await esperar(t, find.text('Ver carrito'));
      await captura(t, '06b_inicio_mas');
      debugPrint('CARRITO desde +: antes=$antesMas despues=${unidadesCarrito(t)}');
      expect(unidadesCarrito(t), greaterThan(antesMas));
      await t.pump(const Duration(seconds: 5));
      await tocar(t, find.text(conStock.nombre));
      await esperar(t, find.text('Agregar al carrito'));
      await captura(t, '07_producto');
      final antes = unidadesCarrito(t);
      await tocar(t, find.text('Agregar al carrito'));
      await esperar(t, find.text('Ir al carrito'));
      await captura(t, '08_confirmacion');
      await tocar(t, find.text('Ir al carrito'));
      await esperar(t, find.text('Total a pagar'));
      await captura(t, '09_carrito');
      final despues = unidadesCarrito(t);
      debugPrint('CARRITO desde producto: antes=$antes despues=$despues');
      expect(despues, greaterThan(antes));
    } finally {
      for (final l in servicio.carrito.items
          .where((l) => l.productoId == conStock.id)
          .toList()) {
        if (cantidadInicial == 0) {
          await servicio.quitar(l.itemId);
        } else {
          await servicio.cambiarCantidad(l.itemId, cantidadInicial);
        }
      }
      debugPrint('CARRITO restaurado: ${conStock.nombre} = ${cantidadDe(conStock.id)}');
    }
  });
}
