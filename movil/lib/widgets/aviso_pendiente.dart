import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Aviso honesto para capacidades todavía no implementadas en esta entrega.
///
/// Evita los botones muertos sin fingir una integración que no existe.
void mostrarModuloPendiente(BuildContext context, String modulo) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text('$modulo: módulo pendiente de implementación.'),
        backgroundColor: Paleta.grisOscuro,
        behavior: SnackBarBehavior.floating,
      ),
    );
}
