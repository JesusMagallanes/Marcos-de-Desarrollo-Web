import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/estado/navegacion.dart';
import '../../nucleo/modelos/pedido.dart';
import '../../widgets/boton_accion.dart';
import '../../widgets/formatos.dart';
import 'pagina_pedidos.dart';

/// Confirmación de compra real.
///
/// Se crea para cuando el backend confirme un pago. Hoy no se abre desde el
/// checkout: no hay proveedor configurado y no se simula ningún cobro.
class PaginaCompraRealizada extends StatelessWidget {
  const PaginaCompraRealizada({
    super.key,
    required this.pedido,
    required this.correo,
  });

  final Pedido pedido;
  final String correo;

  @override
  Widget build(BuildContext context) => PopScope(
        canPop: false,
        onPopInvokedWithResult: (_, _) => _irATienda(context),
        child: Scaffold(
          backgroundColor: const Color(0xFFF8F8F8),
          body: Column(
            children: [
              Expanded(
                child: ListView(
                  padding: EdgeInsets.fromLTRB(
                    24,
                    MediaQuery.paddingOf(context).top + 36,
                    24,
                    16,
                  ),
                  children: [
                    Center(
                      child: Container(
                        width: 112,
                        height: 112,
                        decoration: BoxDecoration(
                          color: Paleta.verdeSuave,
                          shape: BoxShape.circle,
                        ),
                        child: Center(
                          child: Container(
                            width: 64,
                            height: 64,
                            decoration: const BoxDecoration(
                              color: Paleta.acentoVerde,
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.check,
                              size: 36,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 22),
                    const Text(
                      '¡Compra realizada!',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                        color: Paleta.textoPrimario,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Tu pedido #${pedido.numero} fue registrado. '
                      'Te avisaremos al correo que tenemos en tu cuenta.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontSize: 12.5,
                        height: 1.45,
                        color: Paleta.textoSecundario,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: Paleta.bordeTarjeta),
                      ),
                      child: Column(
                        children: [
                          _Dato(
                            icono: Icons.receipt_long_outlined,
                            etiqueta: 'Número de pedido',
                            valor: '#${pedido.numero}',
                          ),
                          const Divider(height: 18),
                          _Dato(
                            icono: Icons.calendar_today_outlined,
                            etiqueta: 'Fecha',
                            valor: pedido.fecha == null
                                ? 'No disponible'
                                : DateFormat('d MMM yyyy', 'es_PE')
                                    .format(pedido.fecha!.toLocal()),
                          ),
                          const Divider(height: 18),
                          _Dato(
                            icono: Icons.mail_outline,
                            etiqueta: 'Correo',
                            valor: correo.isEmpty ? 'No registrado' : correo,
                          ),
                          const Divider(height: 18),
                          _Dato(
                            icono: Icons.payment_outlined,
                            etiqueta: 'Método de pago',
                            valor: pedido.metodoPago.isEmpty
                                ? 'No registrado'
                                : pedido.metodoPago,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: Paleta.verdeSuave,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            'Total pagado',
                            style: TextStyle(
                              fontSize: 13,
                              color: Paleta.verdeOscuro,
                            ),
                          ),
                          Text(
                            soles(pedido.total),
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w800,
                              color: Paleta.verdeOscuro,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                    const Text(
                      'El seguimiento de entrega aparecerá en «Mis pedidos» '
                      'cuando el backend lo registre.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 11.5,
                        color: Paleta.textoSecundario,
                      ),
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
                  left: 20,
                  right: 20,
                  top: 12,
                  bottom: MediaQuery.paddingOf(context).bottom + 12,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    BotonAccion(
                      etiqueta: 'Ver mis pedidos',
                      icono: Icons.receipt_long_outlined,
                      alPulsar: () => Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const PaginaPedidos(),
                        ),
                      ),
                    ),
                    const SizedBox(height: 9),
                    BotonAccion(
                      etiqueta: 'Seguir comprando',
                      verde: false,
                      icono: Icons.shopping_bag_outlined,
                      alPulsar: () => _irATienda(context),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      );

  void _irATienda(BuildContext context) {
    pestanaActivaNotifier.value = 0;
    Navigator.of(context).popUntil((r) => r.isFirst);
  }
}

class _Dato extends StatelessWidget {
  const _Dato({
    required this.icono,
    required this.etiqueta,
    required this.valor,
  });

  final IconData icono;
  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Icon(icono, size: 18, color: Paleta.textoSecundario),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  etiqueta,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Paleta.textoSecundario,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  valor,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ],
            ),
          ),
        ],
      );
}
