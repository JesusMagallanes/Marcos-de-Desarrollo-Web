import 'package:flutter/material.dart';

import '../../../nucleo/config/tema.dart';
import '../../../widgets/boton_accion.dart';
import '../../../widgets/campo_etiquetado.dart';
import '../../../widgets/indicador_pasos.dart';
import 'checkout_estado.dart';

class PasoDatos extends StatelessWidget {
  const PasoDatos({
    super.key,
    required this.draft,
    required this.formulario,
    required this.alCambiar,
    required this.alContinuar,
  });

  final CheckoutDraft draft;
  final GlobalKey<FormState> formulario;
  final VoidCallback alCambiar;
  final VoidCallback alContinuar;

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
              children: [
                const IndicadorPasos(pasoActual: 1),
                const SizedBox(height: 18),
                const Text(
                  '¿Quién realiza la compra?',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 12),
                Form(
                  key: formulario,
                  child: Column(
                    children: [
                      CampoEtiquetado(
                        etiqueta: 'Nombres y apellidos',
                        controlador: draft.nombre,
                        icono: Icons.person_outline,
                        sugerencia: 'Tu nombre completo',
                        validar: (v) => _longitud(v, 'Ingresa tu nombre'),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            flex: 3,
                            child: CampoEtiquetado(
                              etiqueta: 'Correo electrónico',
                              controlador: draft.correo,
                              icono: Icons.mail_outline,
                              teclado: TextInputType.emailAddress,
                              sugerencia: 'nombre@correo.com',
                              validar: _correo,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      CampoEtiquetado(
                        etiqueta: 'Celular',
                        controlador: draft.telefono,
                        icono: Icons.phone_outlined,
                        teclado: TextInputType.phone,
                        sugerencia: '9 dígitos',
                        validar: _celular,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Paleta.bordeTarjeta),
                  ),
                  child: const Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.info_outline, size: 18, color: Paleta.verdeOscuro),
                      SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Documento y comprobante: el backend todavía no define '
                          'estas reglas. No se agregarán datos inventados.',
                          style: TextStyle(
                            fontSize: 11.5,
                            height: 1.4,
                            color: Paleta.textoSecundario,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          _Pie(alContinuar: alContinuar),
        ],
      );

  static String? _longitud(String? valor, String mensaje) {
    final texto = valor?.trim() ?? '';
    if (texto.length < 2) return mensaje;
    return null;
  }

  static String? _correo(String? valor) {
    final texto = valor?.trim() ?? '';
    final patron = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
    if (!patron.hasMatch(texto)) return 'Ingresa un correo válido';
    return null;
  }

  static String? _celular(String? valor) {
    final digitos = (valor ?? '').replaceAll(RegExp(r'\D'), '');
    if (digitos.length != 9) return 'El celular debe tener 9 dígitos';
    return null;
  }
}

class _Pie extends StatelessWidget {
  const _Pie({required this.alContinuar});
  final VoidCallback alContinuar;

  @override
  Widget build(BuildContext context) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: Paleta.bordeTarjeta)),
        ),
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 12,
          bottom: MediaQuery.paddingOf(context).bottom + 12,
        ),
        child: BotonAccion(
          etiqueta: 'Continuar a entrega',
          icono: Icons.arrow_forward,
          alPulsar: alContinuar,
        ),
      );
}
