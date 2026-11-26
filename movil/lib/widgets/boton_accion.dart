import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Botón principal de las acciones del flujo de compra.
class BotonAccion extends StatelessWidget {
  const BotonAccion({
    super.key,
    required this.etiqueta,
    required this.alPulsar,
    this.icono,
    this.cargando = false,
    this.habilitado = true,
    this.verde = true,
    this.alto = 46,
    this.colorTexto,
  });

  final String etiqueta;
  final VoidCallback? alPulsar;
  final IconData? icono;
  final bool cargando;
  final bool habilitado;
  final bool verde;
  final double alto;
  final Color? colorTexto;

  @override
  Widget build(BuildContext context) {
    final activo = habilitado && !cargando && alPulsar != null;
    final foreground = colorTexto ?? (verde ? Paleta.negroTarjeta : Paleta.textoPrimario);

    return SizedBox(
      width: double.infinity,
      height: alto,
      child: FilledButton(
        onPressed: activo ? alPulsar : null,
        style: FilledButton.styleFrom(
          backgroundColor: verde ? Paleta.acentoVerde : Colors.white,
          disabledBackgroundColor: verde
              ? Paleta.acentoVerde.withValues(alpha: 0.45)
              : Paleta.bordeTarjeta,
          foregroundColor: foreground,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(
              color: verde ? Colors.transparent : Paleta.bordeTarjeta,
            ),
          ),
          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
        ),
        child: cargando
            ? SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: verde ? Paleta.negroTarjeta : Paleta.verdeOscuro,
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (icono != null) ...[
                    Icon(icono, size: 18, color: foreground),
                    const SizedBox(width: 8),
                  ],
                  Flexible(
                    child: Text(
                      etiqueta,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
