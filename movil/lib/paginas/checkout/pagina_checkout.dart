import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/modelos/carrito.dart';
import '../../nucleo/modelos/usuario.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/cabecera_seccion.dart';
import 'checkout_estado.dart';
import 'paso_datos.dart';
import 'paso_entrega.dart';
import 'paso_metodo_pago.dart';

/// Checkout de tres pasos. Los pasos comparten un único [CheckoutDraft] y se
/// navega dentro de la misma ruta, de modo que el back stack y los datos se
/// conservan sin duplicar pantallas.
class PaginaCheckout extends StatefulWidget {
  const PaginaCheckout({super.key, required this.carrito});

  final Carrito carrito;

  @override
  State<PaginaCheckout> createState() => _PaginaCheckoutEstado();
}

class _PaginaCheckoutEstado extends State<PaginaCheckout> {
  late final CheckoutDraft _draft;
  final _formularioDatos = GlobalKey<FormState>();
  final _formularioEntrega = GlobalKey<FormState>();

  @override
  void initState() {
    super.initState();
    final usuario = context.read<Sesion>().usuario;
    _draft = CheckoutDraft(
      carrito: widget.carrito,
      usuario: usuario ?? const Usuario(
        id: 0,
        nombre: '',
        apellidos: '',
        email: '',
        telefono: '',
        direccion: '',
        rol: 'CLIENTE',
      ),
    );
  }

  @override
  void dispose() {
    _draft.dispose();
    super.dispose();
  }

  void _irA(int paso) => setState(() => _draft.paso = paso);

  void _volver() {
    if (_draft.paso > 1) {
      _irA(_draft.paso - 1);
    } else {
      Navigator.of(context).maybePop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final titulos = ['Datos de compra', 'Datos de entrega', 'Método de pago'];
    final paso = _draft.paso;

    return PopScope<void>(
      canPop: paso == 1,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && paso > 1) _volver();
      },
      child: Scaffold(
        backgroundColor: const Color(0xFFF8F8F8),
        body: Column(
          children: [
            CabeceraSeccion(
              titulo: titulos[paso - 1],
              subtitulo: 'Paso $paso de 3',
              alVolver: _volver,
            ),
            Expanded(
              child: _vistaPaso(paso),
            ),
          ],
        ),
      ),
    );
  }

  Widget _vistaPaso(int paso) {
    switch (paso) {
      case 1:
        return PasoDatos(
          draft: _draft,
          formulario: _formularioDatos,
          alCambiar: () => setState(() {}),
          alContinuar: () {
            if (_formularioDatos.currentState?.validate() ?? false) _irA(2);
          },
        );
      case 2:
        return PasoEntrega(
          draft: _draft,
          formulario: _formularioEntrega,
          alCambiar: () => setState(() {}),
          alContinuar: () {
            if (_draft.modalidad != 'envio') return;
            if (_formularioEntrega.currentState?.validate() ?? false) _irA(3);
          },
        );
      default:
        return PasoMetodoPago(
          draft: _draft,
          alCambiar: () => setState(() {}),
          alVolver: _volver,
        );
    }
  }
}
