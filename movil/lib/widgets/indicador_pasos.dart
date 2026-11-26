import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Indicador horizontal de los tres pasos del checkout.
class IndicadorPasos extends StatelessWidget {
  const IndicadorPasos({super.key, required this.pasoActual});

  final int pasoActual;
  static const _etiquetas = ['Datos', 'Entrega', 'Pago'];

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final anchoTotal = constraints.maxWidth;
            return Row(
              children: [
                for (var i = 0; i < _etiquetas.length; i++) ...[
                  if (i > 0)
                    Expanded(
                      child: Container(
                        height: 1.5,
                        color: i <= pasoActual ? Paleta.acentoVerde : Paleta.bordeTarjeta,
                      ),
                    ),
                  _Paso(
                    numero: i + 1,
                    etiqueta: _etiquetas[i],
                    completado: i + 1 < pasoActual,
                    activo: i + 1 == pasoActual,
                    ancho: (anchoTotal - 32) / 3,
                  ),
                ],
              ],
            );
          },
        ),
      );
}

class _Paso extends StatelessWidget {
  const _Paso({
    required this.numero,
    required this.etiqueta,
    required this.completado,
    required this.activo,
    required this.ancho,
  });

  final int numero;
  final String etiqueta;
  final bool completado;
  final bool activo;
  final double ancho;

  @override
  Widget build(BuildContext context) {
    final color = completado ? Paleta.verdeOscuro : Paleta.bordeTarjeta;

    return SizedBox(
      width: ancho < 48 ? 48 : ancho,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 24,
            height: 24,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: activo ? Paleta.negroTarjeta : (completado ? Paleta.acentoVerde : Colors.white),
              border: Border.all(color: color, width: 1.5),
            ),
            child: Center(
              child: completado
                  ? const Icon(Icons.check, size: 15, color: Paleta.negroTarjeta)
                  : Text(
                      '$numero',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: activo ? Paleta.acentoVerde : Paleta.textoSecundario,
                      ),
                    ),
            ),
          ),
          const SizedBox(height: 5),
          Text(
            etiqueta,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 11,
              fontWeight: activo ? FontWeight.w700 : FontWeight.w500,
              color: activo || completado
                  ? Paleta.textoPrimario
                  : Paleta.textoSecundario,
            ),
          ),
        ],
      ),
    );
  }
}
