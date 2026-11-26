import 'package:flutter/material.dart';

import '../../../nucleo/api/error_api.dart';
import '../../../nucleo/config/tema.dart';
import '../../../widgets/aviso_pendiente.dart';
import '../../../widgets/boton_accion.dart';
import '../../../widgets/cabecera_seccion.dart';

/// Resultado recuperable del intento de pago.
///
/// Nunca afirma que hubo un cargo fallido salvo que el backend o la pasarela lo
/// hayan dicho: cuando el proveedor no está configurado el estado es
/// explícitamente «pago no iniciado».
class ResultadoPago extends StatelessWidget {
  const ResultadoPago({
    super.key,
    this.sinProveedor = false,
    this.error,
  });

  final bool sinProveedor;
  final ErrorApi? error;

  @override
  Widget build(BuildContext context) {
    final titulo = sinProveedor
        ? 'Pago no disponible'
        : 'No pudimos procesar el pago';
    final explicacion = sinProveedor
        ? 'El proveedor de pago todavía no está configurado. '
            'No se realizó ningún cargo.'
        : 'No se pudo completar la operación. Comprueba el estado antes '
            'de volver a intentarlo.';

    return Scaffold(
      backgroundColor: const Color(0xFFF8F8F8),
      body: Column(
        children: [
          const CabeceraSeccion(
            titulo: 'Método de pago',
            subtitulo: 'Paso 3 de 3',
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 28, 16, 16),
              children: [
                Center(
                  child: Container(
                    width: 116,
                    height: 116,
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFE7E7),
                      shape: BoxShape.circle,
                    ),
                    child: Center(
                      child: Container(
                        width: 66,
                        height: 66,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                          border: Border.all(color: Paleta.rojo, width: 2),
                        ),
                        child: const Icon(Icons.close, size: 34, color: Paleta.rojo),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  titulo,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  explicacion,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 12.5,
                    height: 1.45,
                    color: Paleta.textoSecundario,
                  ),
                ),
                const SizedBox(height: 20),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFF1F1),
                    borderRadius: BorderRadius.circular(13),
                    border: Border.all(
                      color: Paleta.rojo.withValues(alpha: 0.35),
                    ),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.warning_amber_outlined,
                          size: 18, color: Paleta.rojo),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              error?.titulo ?? 'Pago no iniciado',
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                                color: Paleta.rojo,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              error?.mensaje ??
                                  'No hay proveedor configurado; el carrito y los '
                                  'datos del checkout se conservan.',
                              style: const TextStyle(
                                fontSize: 11.5,
                                color: Paleta.textoSecundario,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: Paleta.bordeTarjeta),
                  ),
                  child: Column(
                    children: [
                      const Text(
                        '¿Qué puedes hacer?',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color: Paleta.textoPrimario,
                        ),
                      ),
                      const SizedBox(height: 10),
                      _Consejo(
                        icono: Icons.refresh,
                        texto: 'Volver a revisar el método elegido',
                      ),
                      const SizedBox(height: 9),
                      _Consejo(
                        icono: Icons.payment_outlined,
                        texto: 'Elegir otro método de pago',
                      ),
                      const SizedBox(height: 9),
                      _Consejo(
                        icono: Icons.support_agent_outlined,
                        texto: 'Contactar a soporte',
                        alPulsar: () => mostrarModuloPendiente(context, 'Soporte'),
                      ),
                    ],
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
              left: 16,
              right: 16,
              top: 12,
              bottom: MediaQuery.paddingOf(context).bottom + 12,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                BotonAccion(
                  etiqueta: 'Reintentar pago',
                  icono: Icons.refresh,
                  alPulsar: () => Navigator.of(context).maybePop(),
                ),
                const SizedBox(height: 9),
                BotonAccion(
                  etiqueta: 'Elegir otro método',
                  verde: false,
                  alPulsar: () => Navigator.of(context).maybePop(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Consejo extends StatelessWidget {
  const _Consejo({required this.icono, required this.texto, this.alPulsar});
  final IconData icono;
  final String texto;
  final VoidCallback? alPulsar;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: alPulsar,
        child: Row(
          children: [
            Icon(icono, size: 17, color: Paleta.verdeOscuro),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                texto,
                style: const TextStyle(
                  fontSize: 12,
                  color: Paleta.textoPrimario,
                ),
              ),
            ),
          ],
        ),
      );
}
