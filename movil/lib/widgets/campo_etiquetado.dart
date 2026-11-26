import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';

/// Campo del checkout con etiqueta, icono y espacio para el error de validación.
class CampoEtiquetado extends StatelessWidget {
  const CampoEtiquetado({
    super.key,
    required this.etiqueta,
    required this.controlador,
    this.sugerencia,
    this.icono,
    this.teclado = TextInputType.text,
    this.validar,
    this.ofuscar = false,
    this.habilitado = true,
    this.sufijo,
    this.maxLineas = 1,
  });

  final String etiqueta;
  final TextEditingController controlador;
  final String? sugerencia;
  final IconData? icono;
  final TextInputType teclado;
  final String? Function(String?)? validar;
  final bool ofuscar;
  final bool habilitado;
  final Widget? sufijo;
  final int maxLineas;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            etiqueta,
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: Paleta.textoPrimario,
            ),
          ),
          const SizedBox(height: 5),
          TextFormField(
            controller: controlador,
            keyboardType: teclado,
            obscureText: ofuscar,
            enabled: habilitado,
            maxLines: maxLineas,
            validator: validar,
            decoration: InputDecoration(
              hintText: sugerencia,
              hintStyle: const TextStyle(color: Paleta.textoTenue, fontSize: 13),
              prefixIcon: icono == null
                  ? null
                  : Icon(icono, size: 18, color: Paleta.textoSecundario),
              suffixIcon: sufijo,
              filled: true,
              fillColor: Colors.white,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Paleta.bordeTarjeta),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Paleta.bordeTarjeta),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Paleta.verdeOscuro, width: 1.5),
              ),
              errorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Paleta.rojo),
              ),
              focusedErrorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Paleta.rojo, width: 1.5),
              ),
            ),
            style: const TextStyle(fontSize: 13, color: Paleta.textoPrimario),
          ),
        ],
      );
}
