import 'package:flutter/material.dart';

import '../../../nucleo/config/tema.dart';
import '../../../nucleo/modelos/carrito.dart';
import '../../../nucleo/modelos/direccion.dart';
import '../../../widgets/boton_accion.dart';
import '../../../widgets/formatos.dart';
import '../../../widgets/campo_etiquetado.dart';
import '../../../widgets/indicador_pasos.dart';
import '../direcciones/pagina_direcciones.dart';
import 'checkout_estado.dart';

class PasoEntrega extends StatelessWidget {
  const PasoEntrega({
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
                const IndicadorPasos(pasoActual: 2),
                const SizedBox(height: 16),
                _Modalidad(
                  seleccionada: draft.modalidad,
                  alCambiar: (valor) {
                    draft.modalidad = valor;
                    alCambiar();
                  },
                ),
                const SizedBox(height: 14),
                if (draft.modalidad == 'envio') ...[
                  _ResumenUbicacion(
                    direccion: draft.direccionGuardada,
                    alElegir: () async {
                      final elegida = await Navigator.of(context).push<Direccion>(
                        MaterialPageRoute<Direccion>(
                          builder: (_) => const PaginaDirecciones(desdeCheckout: true),
                        ),
                      );
                      if (elegida == null || !context.mounted) return;
                      draft.aplicarDireccion(elegida);
                      alCambiar();
                    },
                  ),
                  const SizedBox(height: 14),
                  Form(
                    key: formulario,
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Expanded(
                              flex: 3,
                              child: CampoEtiquetado(
                                etiqueta: 'Dirección',
                                controlador: draft.calle,
                                icono: Icons.place_outlined,
                                validar: (v) => _requerido(v, 'Indica la calle'),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              flex: 1,
                              child: CampoEtiquetado(
                                etiqueta: 'N.º',
                                controlador: draft.numero,
                                teclado: TextInputType.number,
                                validar: (v) => _requerido(v, 'Indica el número'),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Departamento',
                                controlador: draft.departamento,
                                validar: (v) => _requerido(v, 'Indica el departamento'),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Provincia',
                                controlador: draft.provincia,
                                validar: (v) => _requerido(v, 'Indica la provincia'),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Distrito',
                                controlador: draft.distrito,
                                validar: (v) => _requerido(v, 'Indica el distrito'),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Código postal',
                                controlador: draft.codigoPostal,
                                teclado: TextInputType.number,
                                validar: _codigoPostal,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        CampoEtiquetado(
                          etiqueta: 'Referencia (opcional)',
                          controlador: draft.referencia,
                          sugerencia: 'Ej.: frente al parque',
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  _CostoEnvio(carrito: draft.carrito),
                ] else
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: Paleta.bordeTarjeta),
                    ),
                    child: const Text(
                      'Recojo en tienda: el backend todavía no publica tiendas '
                      'ni horarios, así que esta modalidad no está disponible.',
                      style: TextStyle(
                        fontSize: 12,
                        height: 1.5,
                        color: Paleta.textoSecundario,
                      ),
                    ),
                  ),
              ],
            ),
          ),
          _Pie(
            habilitado: draft.modalidad == 'envio',
            alContinuar: alContinuar,
          ),
        ],
      );

  static String? _requerido(String? valor, String mensaje) {
    final texto = valor?.trim() ?? '';
    return texto.isEmpty ? mensaje : null;
  }

  static String? _codigoPostal(String? valor) {
    final texto = valor?.trim() ?? '';
    if (!RegExp(r'^[0-9]{5}$').hasMatch(texto)) {
      return 'El código postal son 5 dígitos';
    }
    return null;
  }
}

class _Modalidad extends StatelessWidget {
  const _Modalidad({required this.seleccionada, required this.alCambiar});
  final String seleccionada;
  final ValueChanged<String> alCambiar;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(4),
        decoration: BoxDecoration(
          color: const Color(0xFFEEF0EF),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          children: [
            _Opcion(
              etiqueta: 'Envío a domicilio',
              icono: Icons.local_shipping_outlined,
              activo: seleccionada == 'envio',
              alPulsar: () => alCambiar('envio'),
            ),
            _Opcion(
              etiqueta: 'Recojo en tienda',
              icono: Icons.store_outlined,
              activo: seleccionada == 'recojo',
              alPulsar: () => alCambiar('recojo'),
            ),
          ],
        ),
      );
}

class _Opcion extends StatelessWidget {
  const _Opcion({
    required this.etiqueta,
    required this.icono,
    required this.activo,
    required this.alPulsar,
  });

  final String etiqueta;
  final IconData icono;
  final bool activo;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => Expanded(
        child: InkWell(
          onTap: alPulsar,
          borderRadius: BorderRadius.circular(11),
          child: Container(
            height: 40,
            decoration: BoxDecoration(
              color: activo ? Colors.white : Colors.transparent,
              borderRadius: BorderRadius.circular(11),
              boxShadow: activo
                  ? [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.06),
                        blurRadius: 4,
                      ),
                    ]
                  : null,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  icono,
                  size: 16,
                  color: activo ? Paleta.textoPrimario : Paleta.textoSecundario,
                ),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    etiqueta,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: activo ? FontWeight.w700 : FontWeight.w500,
                      color: activo ? Paleta.textoPrimario : Paleta.textoSecundario,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}

class _ResumenUbicacion extends StatelessWidget {
  const _ResumenUbicacion({required this.direccion, required this.alElegir});
  final Direccion? direccion;
  final VoidCallback alElegir;

  @override
  Widget build(BuildContext context) => Container(
        height: 108,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Paleta.fondoClaro,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Stack(
          children: [
            Center(
              child: Icon(
                Icons.location_on,
                size: 32,
                color: Paleta.rojo,
              ),
            ),
            Align(
              alignment: Alignment.bottomLeft,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Paleta.verdeSuave,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      direccion == null
                          ? 'Dirección pendiente'
                          : 'Dirección registrada',
                      style: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        color: Paleta.textoPrimario,
                      ),
                    ),
                  ),
                  const SizedBox(width: 6),
                  TextButton(
                    onPressed: alElegir,
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 6),
                      minimumSize: const Size(0, 28),
                    ),
                    child: const Text('Elegir guardada', style: TextStyle(fontSize: 11)),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
}

class _CostoEnvio extends StatelessWidget {
  const _CostoEnvio({required this.carrito});
  final Carrito carrito;

  @override
  Widget build(BuildContext context) {
    final gratis = carrito.envioGratis;
    final costo = carrito.costoEnvio;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Paleta.bordeTarjeta),
      ),
      child: Row(
        children: [
          Icon(Icons.schedule, size: 20, color: Paleta.verdeOscuro),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Envío calculado por el servidor',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  gratis ? 'Envío sin costo' : 'Costo confirmado al crear el pedido',
                  style: const TextStyle(
                    fontSize: 11,
                    color: Paleta.textoSecundario,
                  ),
                ),
              ],
            ),
          ),
          Text(
            gratis ? 'GRATIS' : soles(costo),
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w800,
              color: gratis ? Paleta.verdeOscuro : Paleta.textoPrimario,
            ),
          ),
        ],
      ),
    );
  }
}

class _Pie extends StatelessWidget {
  const _Pie({required this.habilitado, required this.alContinuar});
  final bool habilitado;
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
          etiqueta: habilitado ? 'Continuar al pago' : 'Recojo no disponible',
          icono: Icons.arrow_forward,
          habilitado: habilitado,
          alPulsar: alContinuar,
        ),
      );
}
