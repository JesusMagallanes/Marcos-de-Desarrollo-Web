import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../nucleo/api/error_api.dart';
import '../../../nucleo/config/tema.dart';
import '../../../nucleo/modelos/metodo_pago.dart';
import '../../../nucleo/servicios/compras_servicio.dart';
import '../../../widgets/boton_accion.dart';
import '../../../widgets/formatos.dart';
import '../../../widgets/indicador_pasos.dart';
import 'checkout_estado.dart';
import 'datos_tarjeta.dart';
import 'resultado_pago.dart';

class PasoMetodoPago extends StatefulWidget {
  const PasoMetodoPago({
    super.key,
    required this.draft,
    required this.alCambiar,
    required this.alVolver,
  });

  final CheckoutDraft draft;
  final VoidCallback alCambiar;
  final VoidCallback alVolver;

  @override
  State<PasoMetodoPago> createState() => _PasoMetodoPagoEstado();
}

class _PasoMetodoPagoEstado extends State<PasoMetodoPago> {
  late final Future<List<MetodoPago>> _metodos;

  @override
  void initState() {
    super.initState();
    _metodos = context.read<ComprasServicio>().metodosPago();
  }

  void _recargar() {
    setState(() {
      _metodos = context.read<ComprasServicio>().metodosPago();
    });
  }

  void _confirmar() {
    final metodo = widget.draft.metodoSeleccionado;
    if (metodo == null) return;

    if (metodo.requierePasarela) {
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => DatosTarjeta(draft: widget.draft, metodo: metodo),
        ),
      );
      return;
    }

    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => const ResultadoPago(sinProveedor: true),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
              children: [
                const IndicadorPasos(pasoActual: 3),
                const SizedBox(height: 18),
                const Text(
                  'Elige cómo pagar',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 12),
                FutureBuilder<List<MetodoPago>>(
                  future: _metodos,
                  builder: (context, estado) {
                    if (estado.connectionState == ConnectionState.waiting) {
                      return const Padding(
                        padding: EdgeInsets.all(24),
                        child: Center(
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                      );
                    }
                    if (estado.hasError) {
                      final error = estado.error;
                      final mensaje = error is ErrorApi
                          ? error.mensaje
                          : 'No pudimos cargar los métodos de pago.';
                      return _ErrorMetodos(mensaje: mensaje, alReintentar: _recargar);
                    }
                    final metodos = estado.data ?? const <MetodoPago>[];
                    if (metodos.isEmpty) {
                      return const _ErrorMetodos(
                        mensaje: 'El backend no tiene métodos de pago habilitados.',
                      );
                    }
                    return Column(
                      children: [
                        for (final metodo in metodos) ...[
                          _TarjetaMetodo(
                            metodo: metodo,
                            seleccionado: widget.draft.metodoSeleccionado?.id == metodo.id,
                            alSeleccionar: () {
                              setState(() => widget.draft.metodoSeleccionado = metodo);
                              widget.alCambiar();
                            },
                          ),
                          const SizedBox(height: 7),
                        ],
                      ],
                    );
                  },
                ),
                TextButton(
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => DatosTarjeta(
                        draft: widget.draft,
                        metodo: MetodoPago(
                          id: 0,
                          nombre: 'Tarjeta',
                          descripcion: 'Formulario seguro',
                          tipo: 'MERCADOPAGO',
                        ),
                      ),
                    ),
                  ),
                  child: const Text(
                    'Ver formulario de tarjeta (sin cobro)',
                    style: TextStyle(fontSize: 11),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: Paleta.bordeTarjeta),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Total a pagar',
                        style: TextStyle(fontSize: 12, color: Paleta.textoSecundario),
                      ),
                      Text(
                        soles(widget.draft.carrito.total),
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w800,
                          color: Paleta.textoPrimario,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                const Row(
                  children: [
                    Icon(Icons.shield_outlined, size: 15, color: Paleta.verdeOscuro),
                    SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Conexión cifrada. Los datos de tarjeta no se guardan en '
                        'el dispositivo ni se envían a este servidor.',
                        style: TextStyle(
                          fontSize: 10.5,
                          color: Paleta.textoSecundario,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          _Pie(
            seleccionado: widget.draft.metodoSeleccionado,
            total: widget.draft.carrito.total,
            alConfirmar: _confirmar,
          ),
        ],
      );
}

class _TarjetaMetodo extends StatelessWidget {
  const _TarjetaMetodo({
    required this.metodo,
    required this.seleccionado,
    required this.alSeleccionar,
  });

  final MetodoPago metodo;
  final bool seleccionado;
  final VoidCallback alSeleccionar;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: alSeleccionar,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          height: 60,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: seleccionado ? Paleta.verdeSuave : Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: seleccionado ? Paleta.negroTarjeta : Paleta.bordeTarjeta,
              width: seleccionado ? 1.6 : 1,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: seleccionado ? Paleta.negroTarjeta : Paleta.fondoClaro,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  metodo.icono,
                  size: 19,
                  color: seleccionado ? Paleta.acentoVerde : Paleta.textoSecundario,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      metodo.nombre,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w700,
                        color: Paleta.textoPrimario,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      metodo.descripcion.isEmpty ? metodo.tipo : metodo.descripcion,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 10.5,
                        color: Paleta.textoSecundario,
                      ),
                    ),
                  ],
                ),
              ),
              Icon(
                seleccionado ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                size: 19,
                color: seleccionado ? Paleta.negroTarjeta : Paleta.textoTenue,
              ),
            ],
          ),
        ),
      );
}

class _ErrorMetodos extends StatelessWidget {
  const _ErrorMetodos({required this.mensaje, this.alReintentar});
  final String mensaje;
  final VoidCallback? alReintentar;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Column(
          children: [
            Text(
              mensaje,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 12, color: Paleta.textoSecundario),
            ),
            if (alReintentar != null)
              TextButton(onPressed: alReintentar, child: const Text('Reintentar')),
          ],
        ),
      );
}

class _Pie extends StatelessWidget {
  const _Pie({
    required this.seleccionado,
    required this.total,
    required this.alConfirmar,
  });

  final MetodoPago? seleccionado;
  final double total;
  final VoidCallback alConfirmar;

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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (seleccionado == null)
              const Padding(
                padding: EdgeInsets.only(bottom: 8),
                child: Text(
                  'Selecciona un método para continuar',
                  style: TextStyle(fontSize: 11, color: Paleta.textoSecundario),
                ),
              ),
            BotonAccion(
              etiqueta: seleccionado == null
                  ? 'Selecciona un método'
                  : 'Continuar con ${seleccionado!.nombre} · ${soles(total)}',
              icono: Icons.lock_outlined,
              habilitado: seleccionado != null,
              alPulsar: alConfirmar,
            ),
          ],
        ),
      );
}
