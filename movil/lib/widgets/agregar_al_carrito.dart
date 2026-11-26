import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../nucleo/config/tema.dart';
import '../nucleo/estado/navegacion.dart';
import '../nucleo/servicios/carrito_servicio.dart';
import '../nucleo/servicios/sesion.dart';
import '../paginas/login/pagina_login.dart';

/// Añade una unidad de [productoId] al carrito desde cualquier pantalla que no
/// sea la ficha (tarjetas del inicio, recomendaciones del chat).
///
/// Hace lo mismo que la ficha: el carrito vive en el servidor y va atado al
/// usuario del token, así que sin sesión primero se pide entrar y, si entra,
/// se continúa. El resultado se confirma con lo que respondió el servidor, no
/// se da por hecho: un aviso de "agregado" sin agregar es peor que ninguno.
Future<void> agregarAlCarrito(
  BuildContext context, {
  required int productoId,
  required String nombre,
}) async {
  final mensajero = ScaffoldMessenger.of(context);
  final navegador = Navigator.of(context);
  final carrito = context.read<CarritoServicio>();

  if (!context.read<Sesion>().autenticado) {
    final entro = await navegador.push<bool>(
      MaterialPageRoute(
        builder: (_) => const PaginaLogin(motivo: 'Entra para añadir al carrito'),
      ),
    );
    if (entro != true) return;
  }

  await carrito.agregar(productoId, 1);

  final error = carrito.error;
  mensajero
    ..hideCurrentSnackBar()
    ..showSnackBar(
      error != null
          ? SnackBar(
              content: Text(error.mensajeCompleto),
              backgroundColor: Paleta.rojo,
              behavior: SnackBarBehavior.floating,
            )
          : SnackBar(
              content: Text('$nombre agregado al carrito'),
              behavior: SnackBarBehavior.floating,
              action: SnackBarAction(
                label: 'Ver carrito',
                onPressed: () {
                  pestanaActivaNotifier.value = 2;
                  navegador.popUntil((ruta) => ruta.isFirst);
                },
              ),
            ),
    );
}
