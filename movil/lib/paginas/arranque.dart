import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../nucleo/servicios/carrito_servicio.dart';
import '../nucleo/servicios/catalogo_servicio.dart';
import '../nucleo/servicios/sesion.dart';
import 'inicio/armazon.dart';
import 'pantalla_carga.dart';

/// Arranque de la app, con el mismo criterio que la web.
///
/// La pantalla de carga NO dura un tiempo fijo: dura lo que tarde la app en
/// estar lista de verdad. En la web eso es `ApplicationRef.whenStable()`, que
/// además de arrancar Angular espera a que respondan las peticiones de la
/// primera pantalla. Aquí se hace lo mismo a mano, porque en Flutter no hay un
/// indicador equivalente: se espera a recuperar la sesión Y a que llegue el
/// catálogo que va a pintar la tienda.
///
/// Precargar aquí tiene un segundo efecto: `CatalogoServicio` cachea, así que
/// cuando la tienda se monta ya tiene los datos y entra pintada, sin su propio
/// giro de carga. Sin esto la pantalla se quitaría para dejar ver... otro
/// spinner, que era justo lo que se arregló en la web.
class Arranque extends StatefulWidget {
  const Arranque({super.key});

  @override
  State<Arranque> createState() => _ArranqueEstado();
}

class _ArranqueEstado extends State<Arranque> {
  /// Suelo anti-parpadeo. No es "la duración": es lo mínimo para que la
  /// animación se lea como intencionada. Sin él, un arranque de 80 ms enciende
  /// y apaga la pantalla de golpe y parece un fallo de pintado.
  static const _minimoVisible = Duration(milliseconds: 400);

  /// Techo. Si la API no contesta —está caída, el móvil sin cobertura—, sin
  /// este límite el usuario se quedaría mirando la animación para siempre.
  /// Pasado el tope se entra igual: cada pantalla sabe enseñar su propio error,
  /// que es mucho más útil que un giro eterno.
  static const _maximoEspera = Duration(seconds: 15);

  /// A partir de aquí se avisa de que la conexión va lenta, como en la web.
  static const _avisoLentitud = Duration(milliseconds: 3500);

  bool _listo = false;
  bool _lento = false;
  Timer? _temporizadorAviso;

  @override
  void initState() {
    super.initState();
    _temporizadorAviso = Timer(_avisoLentitud, () {
      if (mounted) setState(() => _lento = true);
    });
    _preparar();
  }

  @override
  void dispose() {
    _temporizadorAviso?.cancel();
    super.dispose();
  }

  Future<void> _preparar() async {
    final reloj = Stopwatch()..start();

    // Se leen ANTES del primer `await`: usar el context después de una espera
    // es un error si el widget ya se desmontó.
    final sesion = context.read<Sesion>();
    final catalogo = context.read<CatalogoServicio>();
    final carrito = context.read<CarritoServicio>();

    /// Envuelve una carga para que un fallo no impida entrar: la tienda ya sabe
    /// enseñar su propio error y reintentar, y quedarse en la pantalla de carga
    /// por una API caída sería peor que pasar y avisar dentro.
    Future<void> sinRomper(Future<Object?> Function() carga) async {
      try {
        await carga();
      } catch (_) {
        // Se ignora a propósito; la pantalla de destino lo vuelve a intentar.
      }
    }

    Future<void> trabajo() async {
      // La sesión primero: si hay usuario, el carrito se pide con su token.
      await sinRomper(sesion.restaurar);

      await Future.wait([
        sinRomper(catalogo.productos),
        sinRomper(catalogo.categorias),
        if (sesion.autenticado) sinRomper(carrito.cargar),
      ]);
    }

    await Future.any([
      trabajo(),
      Future.delayed(_maximoEspera),
    ]);

    final restante = _minimoVisible - reloj.elapsed;
    if (restante > Duration.zero) {
      await Future.delayed(restante);
    }

    if (mounted) {
      _temporizadorAviso?.cancel();
      setState(() => _listo = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    // El cruce se hace con una transición suave, como el desvanecido de 0,6 s
    // que la web aplica al quitar la suya.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 600),
      child: _listo
          ? const Armazon(key: ValueKey('tienda'))
          : PantallaCarga(key: const ValueKey('carga'), mostrarAvisoLento: _lento),
    );
  }
}
