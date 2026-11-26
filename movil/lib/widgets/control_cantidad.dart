import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Cápsula de cantidad con operaciones independientes.
class ControlCantidad extends StatelessWidget {
  const ControlCantidad({
    super.key,
    required this.cantidad,
    required this.minimo,
    required this.maximo,
    required this.alDisminuir,
    required this.alAumentar,
    this.ocupado = false,
  });

  final int cantidad;
  final int minimo;
  final int maximo;
  final VoidCallback alDisminuir;
  final VoidCallback alAumentar;
  final bool ocupado;

  @override
  Widget build(BuildContext context) {
    final puedeBajar = !ocupado && cantidad > minimo;
    final puedeSubir = !ocupado && cantidad < maximo;

    return Container(
      height: 32,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Paleta.bordeTarjeta),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _Boton(
            icono: Icons.remove,
            habilitado: puedeBajar,
            alPulsar: alDisminuir,
          ),
          SizedBox(
            width: 26,
            child: Text(
              '$cantidad',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: Paleta.textoPrimario,
              ),
            ),
          ),
          _Boton(
            icono: Icons.add,
            habilitado: puedeSubir,
            alPulsar: alAumentar,
          ),
        ],
      ),
    );
  }
}

class _Boton extends StatelessWidget {
  const _Boton({required this.icono, required this.habilitado, required this.alPulsar});

  final IconData icono;
  final bool habilitado;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: habilitado ? alPulsar : null,
        borderRadius: BorderRadius.circular(12),
        child: SizedBox(
          width: 32,
          height: 32,
          child: Icon(
            icono,
            size: 16,
            color: habilitado ? Paleta.textoPrimario : Paleta.textoTenue,
          ),
        ),
      );
}
