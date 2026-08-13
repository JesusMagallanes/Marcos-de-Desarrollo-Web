import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../nucleo/config/tema.dart';
import '../nucleo/servicios/sesion.dart';
import 'inicio/armazon.dart';

/// Pantalla de arranque.
///
/// Mientras [Sesion.restaurar] comprueba si quedaba una sesión de una vez
/// anterior, se enseña el logotipo. Sin esta espera se vería el login durante
/// un instante a quien ya tenía la cuenta iniciada, que es justo lo contrario
/// de lo que se busca.
///
/// La tienda se puede mirar sin cuenta, así que al terminar siempre se entra
/// al armazón; el login solo aparece cuando hace falta (carrito, perfil).
class Arranque extends StatelessWidget {
  const Arranque({super.key});

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<Sesion>();

    if (sesion.cargando) {
      return const _PantallaCarga();
    }
    return const Armazon();
  }
}

class _PantallaCarga extends StatelessWidget {
  const _PantallaCarga();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(
                color: Paleta.verdeSuave,
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(Icons.storefront, size: 44, color: Paleta.verde),
            ),
            const SizedBox(height: 24),
            const Text(
              'SmartZone',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w800,
                color: Paleta.texto,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 28),
            const SizedBox(
              width: 150,
              child: LinearProgressIndicator(
                minHeight: 4,
                backgroundColor: Paleta.borde,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
