import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Chip compacto para estados de pedido y filtros.
class ChipEstado extends StatelessWidget {
  const ChipEstado({
    super.key,
    required this.etiqueta,
    this.fondo,
    this.colorTexto,
    this.borde,
  });

  final String etiqueta;
  final Color? fondo;
  final Color? colorTexto;
  final Color? borde;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
        decoration: BoxDecoration(
          color: fondo ?? Paleta.verdeSuave,
          borderRadius: BorderRadius.circular(20),
          border: borde == null ? null : Border.all(color: borde!),
        ),
        child: Text(
          etiqueta,
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            color: colorTexto ?? Paleta.verdeOscuro,
          ),
        ),
      );
}
