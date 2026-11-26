import 'package:flutter/material.dart';

import '../../../nucleo/config/tema.dart';
import '../../../nucleo/modelos/metodo_pago.dart';
import '../../../widgets/boton_accion.dart';
import '../../../widgets/cabecera_seccion.dart';
import '../../../widgets/campo_etiquetado.dart';
import '../../../widgets/formatos.dart';
import 'checkout_estado.dart';
import 'resultado_pago.dart';

/// Vista previa y formulario de tarjeta seguros.
///
/// Los datos viven solo en controladores de esta pantalla: no se escriben en
/// [CheckoutDraft], no se envían al backend propio y se limpian al salir. El
/// botón no ejecuta un cobro porque todavía no hay proveedor configurado.
class DatosTarjeta extends StatefulWidget {
  const DatosTarjeta({super.key, required this.draft, required this.metodo});

  final CheckoutDraft draft;
  final MetodoPago metodo;

  @override
  State<DatosTarjeta> createState() => _DatosTarjetaEstado();
}

class _DatosTarjetaEstado extends State<DatosTarjeta> {
  final _formulario = GlobalKey<FormState>();
  final _numero = TextEditingController();
  final _vencimiento = TextEditingController();
  final _cvv = TextEditingController();
  final _titular = TextEditingController();
  final _cuotas = TextEditingController(text: 'Sin cuotas');

  @override
  void initState() {
    super.initState();
    _numero.addListener(() => setState(() {}));
    _titular.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _numero
      ..clear()
      ..dispose();
    _cvv
      ..clear()
      ..dispose();
    _vencimiento.dispose();
    _titular.dispose();
    _cuotas.dispose();
    super.dispose();
  }

  String get _ultimos4 {
    final digitos = _numero.text.replaceAll(RegExp(r'\D'), '');
    return digitos.length >= 4 ? digitos.substring(digitos.length - 4) : '••••';
  }

  void _continuar() {
    if (!(_formulario.currentState?.validate() ?? false)) return;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => const ResultadoPago(sinProveedor: true),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8F8F8),
        body: Column(
          children: [
            const CabeceraSeccion(
              titulo: 'Datos de la tarjeta',
              subtitulo: 'Paso 3 de 3',
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                children: [
                  _VistaPrevia(ultimos4: _ultimos4, titular: _titular.text),
                  const SizedBox(height: 18),
                  Form(
                    key: _formulario,
                    child: Column(
                      children: [
                        CampoEtiquetado(
                          etiqueta: 'Número de tarjeta',
                          controlador: _numero,
                          icono: Icons.credit_card_outlined,
                          teclado: TextInputType.number,
                          sugerencia: '•••• •••• •••• ••••',
                          validar: (v) {
                            final digitos = (v ?? '').replaceAll(RegExp(r'\D'), '');
                            if (digitos.length < 13) {
                              return 'Revisa el número de tarjeta';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Vencimiento',
                                controlador: _vencimiento,
                                sugerencia: 'MM/AA',
                                teclado: TextInputType.datetime,
                                validar: (v) => RegExp(r'^\d{2}/\d{2}$')
                                        .hasMatch((v ?? '').trim())
                                    ? null
                                    : 'Usa el formato MM/AA',
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'CVV',
                                controlador: _cvv,
                                icono: Icons.help_outline,
                                ofuscar: true,
                                teclado: TextInputType.number,
                                validar: (v) {
                                  final digitos = (v ?? '').replaceAll(RegExp(r'\D'), '');
                                  return digitos.length >= 3 ? null : 'CVV inválido';
                                },
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        CampoEtiquetado(
                          etiqueta: 'Titular de la tarjeta',
                          controlador: _titular,
                          icono: Icons.person_outline,
                          validar: (v) => (v ?? '').trim().length >= 2
                              ? null
                              : 'Indica el titular',
                        ),
                        const SizedBox(height: 12),
                        CampoEtiquetado(
                          etiqueta: 'Cuotas',
                          controlador: _cuotas,
                          habilitado: false,
                          sufijo: const Icon(Icons.expand_more, size: 18),
                        ),
                        const SizedBox(height: 14),
                        Row(
                          children: [
                            SizedBox(
                              width: 20,
                              height: 20,
                              child: Checkbox(
                                value: false,
                                onChanged: null,
                                activeColor: Paleta.acentoVerde,
                              ),
                            ),
                            const SizedBox(width: 8),
                            const Expanded(
                              child: Text(
                                'Guardar tarjeta para próximas compras',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Paleta.textoSecundario,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'El guardado de tarjetas requiere tokenización del proveedor '
                    'y consentimiento explícito; no está habilitado.',
                    style: TextStyle(fontSize: 10.5, color: Paleta.textoSecundario),
                  ),
                ],
              ),
            ),
            Container(
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
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(bottom: 8),
                    child: Text(
                      'El cobro está bloqueado hasta configurar la pasarela. '
                      'No se realizará ningún cargo.',
                      style: TextStyle(fontSize: 10.5, color: Paleta.textoSecundario),
                    ),
                  ),
                  BotonAccion(
                    etiqueta: 'Pagar ${soles(widget.draft.carrito.total)}',
                    icono: Icons.lock_outlined,
                    alPulsar: _continuar,
                  ),
                ],
              ),
            ),
          ],
        ),
      );
}

class _VistaPrevia extends StatelessWidget {
  const _VistaPrevia({required this.ultimos4, required this.titular});

  final String ultimos4;
  final String titular;

  @override
  Widget build(BuildContext context) => AspectRatio(
        aspectRatio: 1.65,
        child: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: Paleta.negroTarjeta,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'SmartZone Pay',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Paleta.acentoVerde,
                    ),
                  ),
                  const Icon(Icons.contactless, size: 20, color: Colors.white70),
                ],
              ),
              const Spacer(),
              Text(
                '•••• •••• •••• $ultimos4',
                style: const TextStyle(
                  fontSize: 19,
                  letterSpacing: 1.2,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 14),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    titular.trim().toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11, color: Colors.white70),
                  ),
                  const Text(
                    'MM/AA',
                    style: TextStyle(fontSize: 11, color: Colors.white70),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
}
