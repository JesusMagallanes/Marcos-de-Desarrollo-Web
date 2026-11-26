import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Cabecera blanca compartida por las pantallas del flujo de compra.
///
/// No sustituye a la barra nativa: solo dibuja el contenido que va debajo del
/// area segura del sistema.
class CabeceraSeccion extends StatelessWidget {
  const CabeceraSeccion({
    super.key,
    required this.titulo,
    this.subtitulo,
    this.alVolver,
    this.accion,
    this.mostrarVolver = true,
  });

  final String titulo;
  final String? subtitulo;
  final VoidCallback? alVolver;
  final Widget? accion;
  final bool mostrarVolver;

  @override
  Widget build(BuildContext context) {
    final superior = MediaQuery.paddingOf(context).top;

    return Container(
      width: double.infinity,
      color: Colors.white,
      padding: EdgeInsets.only(top: superior),
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(bottom: BorderSide(color: Paleta.bordeTarjeta)),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          children: [
            SizedBox(
              width: 40,
              height: 40,
              child: mostrarVolver
                  ? OutlinedButton(
                      onPressed: alVolver ?? () => Navigator.of(context).maybePop(),
                      style: OutlinedButton.styleFrom(
                        padding: EdgeInsets.zero,
                        side: const BorderSide(color: Paleta.bordeTarjeta),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        foregroundColor: Paleta.textoPrimario,
                      ),
                      child: const Icon(Icons.arrow_back_ios_new, size: 18),
                    )
                  : null,
            ),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    titulo,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: Paleta.textoPrimario,
                    ),
                  ),
                  if (subtitulo != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitulo!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 11,
                        color: Paleta.textoSecundario,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            SizedBox(
              width: 40,
              height: 40,
              child: accion ?? const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}
