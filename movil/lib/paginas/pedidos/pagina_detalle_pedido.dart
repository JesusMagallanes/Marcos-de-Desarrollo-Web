import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/pedido.dart';
import '../../nucleo/servicios/compras_servicio.dart';
import '../../widgets/boton_accion.dart';
import '../../widgets/cabecera_seccion.dart';
import '../../widgets/chip_estado.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../producto/pagina_producto.dart';

/// Detalle de una compra real. Los estados salen del pedido y del envío que
/// guarda el backend; no se calculan fechas ni ubicaciones inventadas.
class PaginaDetallePedido extends StatefulWidget {
  const PaginaDetallePedido({super.key, required this.pedidoId});

  final int pedidoId;

  @override
  State<PaginaDetallePedido> createState() => _PaginaDetallePedidoEstado();
}

class _PaginaDetallePedidoEstado extends State<PaginaDetallePedido> {
  late final Future<Pedido> _pedido;
  late final Future<List<Envio>> _envios;

  @override
  void initState() {
    super.initState();
    final compras = context.read<ComprasServicio>();
    _pedido = compras.pedido(widget.pedidoId);
    _envios = compras.misEnvios();
  }

  void _recargar() {
    final compras = context.read<ComprasServicio>();
    setState(() {
      _pedido = compras.pedido(widget.pedidoId);
      _envios = compras.misEnvios();
    });
  }

  void _volverAComprar(Pedido pedido) {
    if (pedido.detalles.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Este pedido no tiene productos disponibles.')),
      );
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PaginaProducto(productoId: pedido.detalles.first.productoId),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8F8F8),
        body: Column(
          children: [
            const CabeceraSeccion(titulo: 'Detalle del pedido'),
            Expanded(
              child: FutureBuilder<Pedido>(
                future: _pedido,
                builder: (context, estadoPedido) {
                  if (estadoPedido.connectionState == ConnectionState.waiting) {
                    return const Center(
                      child: CircularProgressIndicator(strokeWidth: 2),
                    );
                  }
                  if (estadoPedido.hasError || estadoPedido.data == null) {
                    final error = estadoPedido.error;
                    return _Mensaje(
                      texto: error is ErrorApi
                          ? error.mensaje
                          : 'No pudimos cargar el pedido.',
                      alReintentar: _recargar,
                    );
                  }
                  final pedido = estadoPedido.data!;
                  return ListView(
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                    children: [
                      _CabeceraPedido(pedido: pedido),
                      const SizedBox(height: 14),
                      _Trazabilidad(pedido: pedido),
                      const SizedBox(height: 14),
                      FutureBuilder<List<Envio>>(
                        future: _envios,
                        builder: (context, estadoEnvios) {
                          if (estadoEnvios.connectionState == ConnectionState.waiting) {
                            return const _TarjetaCargando(titulo: 'Seguimiento');
                          }
                          final envios = estadoEnvios.data ?? const <Envio>[];
                          Envio? envio;
                          for (final e in envios) {
                            if (e.pedidoId == pedido.id) {
                              envio = e;
                              break;
                            }
                          }
                          return envio == null
                              ? const _SeguimientoSinDatos()
                              : _Seguimiento(envio: envio);
                        },
                      ),
                      const SizedBox(height: 14),
                      _Productos(pedido: pedido),
                      const SizedBox(height: 14),
                      _Resumen(pedido: pedido),
                    ],
                  );
                },
              ),
            ),
            FutureBuilder<Pedido>(
              future: _pedido,
              builder: (context, estado) {
                final pedido = estado.data;
                return Container(
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
                        etiqueta: 'Volver a mis pedidos',
                        alPulsar: () => Navigator.of(context).maybePop(),
                      ),
                      if (pedido != null && !pedido.cancelado) ...[
                        const SizedBox(height: 9),
                        BotonAccion(
                          etiqueta: 'Volver a comprar',
                          verde: false,
                          icono: Icons.shopping_bag_outlined,
                          alPulsar: () => _volverAComprar(pedido),
                        ),
                      ],
                    ],
                  ),
                );
              },
            ),
          ],
        ),
      );
}

class _CabeceraPedido extends StatelessWidget {
  const _CabeceraPedido({required this.pedido});
  final Pedido pedido;

  @override
  Widget build(BuildContext context) {
    final fecha = pedido.fecha == null
        ? 'Fecha no disponible'
        : DateFormat('d MMM yyyy', 'es_PE').format(pedido.fecha!.toLocal());

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Paleta.bordeTarjeta),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Pedido #${pedido.numero}',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ),
              ChipEstado(
                etiqueta: pedido.etiquetaEstado,
                fondo: pedido.cancelado
                    ? const Color(0xFFFFE7E7)
                    : pedido.estado == 'EN_TRANSITO'
                        ? const Color(0xFFFDE8D0)
                        : Paleta.verdeSuave,
                colorTexto: pedido.cancelado ? Paleta.rojo : Paleta.verdeOscuro,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            fecha,
            style: const TextStyle(fontSize: 12, color: Paleta.textoSecundario),
          ),
          const SizedBox(height: 4),
          Text(
            'Pago: ${pedido.metodoPago.isEmpty ? "no registrado" : pedido.metodoPago}',
            style: const TextStyle(fontSize: 12, color: Paleta.textoSecundario),
          ),
        ],
      ),
    );
  }
}

class _Trazabilidad extends StatelessWidget {
  const _Trazabilidad({required this.pedido});
  final Pedido pedido;

  @override
  Widget build(BuildContext context) {
    final pasos = pedido.cancelado
        ? ['Pedido creado', 'Cancelado']
        : ['Pedido confirmado', 'En preparación', 'En reparto', 'Entregado'];

    final indice = pedido.cancelado
        ? 1
        : switch (pedido.estado) {
            'EN_TRANSITO' => 2,
            'ENTREGADO' => 3,
            'PAGADO' || 'CONFIRMADO' => 1,
            _ => 0,
          };

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Paleta.bordeTarjeta),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Estado del pedido',
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: Paleta.textoPrimario,
            ),
          ),
          const SizedBox(height: 12),
          for (var i = 0; i < pasos.length; i++)
            _Paso(
              texto: pasos[i],
              completado: i <= indice,
              actual: i == indice,
              esUltimo: i == pasos.length - 1,
            ),
        ],
      ),
    );
  }
}

class _Paso extends StatelessWidget {
  const _Paso({
    required this.texto,
    required this.completado,
    required this.actual,
    required this.esUltimo,
  });

  final String texto;
  final bool completado;
  final bool actual;
  final bool esUltimo;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 22,
            child: Column(
              children: [
                Container(
                  width: 18,
                  height: 18,
                  decoration: BoxDecoration(
                    color: completado ? Paleta.acentoVerde : Paleta.fondoClaro,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: actual ? Paleta.negroTarjeta : Paleta.bordeTarjeta,
                      width: actual ? 2 : 1,
                    ),
                  ),
                  child: completado
                      ? const Icon(Icons.check, size: 12, color: Colors.white)
                      : null,
                ),
                if (!esUltimo)
                  Container(
                    width: 2,
                    height: 24,
                    color: completado ? Paleta.acentoVerde : Paleta.bordeTarjeta,
                  ),
              ],
            ),
          ),
          const SizedBox(width: 10),
          Padding(
            padding: const EdgeInsets.only(top: 1),
            child: Text(
              texto,
              style: TextStyle(
                fontSize: 12,
                fontWeight: actual ? FontWeight.w700 : FontWeight.w500,
                color: completado ? Paleta.textoPrimario : Paleta.textoTenue,
              ),
            ),
          ),
        ],
      );
}

class _Seguimiento extends StatelessWidget {
  const _Seguimiento({required this.envio});
  final Envio envio;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.local_shipping_outlined,
                    size: 18, color: Paleta.verdeOscuro),
                const SizedBox(width: 8),
                const Expanded(
                  child: Text(
                    'Seguimiento de entrega',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Paleta.textoPrimario,
                    ),
                  ),
                ),
                ChipEstado(etiqueta: envio.etiquetaEstado),
              ],
            ),
            const SizedBox(height: 12),
            _Fila(
              icono: Icons.place_outlined,
              texto: envio.direccion.isEmpty ? 'Dirección no registrada' : envio.direccion,
            ),
            if (envio.referencia != null && envio.referencia!.trim().isNotEmpty)
              _Fila(icono: Icons.info_outline, texto: 'Ref.: ${envio.referencia}'),
            if (envio.telefonoContacto != null && envio.telefonoContacto!.trim().isNotEmpty)
              _Fila(icono: Icons.phone_outlined, texto: envio.telefonoContacto!),
            if (envio.fechaEntregada != null)
              _Fila(
                icono: Icons.check_circle_outline,
                texto: 'Entregado el ${DateFormat("d MMM yyyy", "es_PE").format(envio.fechaEntregada!.toLocal())}',
              ),
            if (envio.minutosEstimados != null)
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Text(
                  envio.distanciaEsEstimada
                      ? 'Tiempo estimado por el backend: ${envio.minutosEstimados} min'
                      : 'Tiempo de entrega: ${envio.minutosEstimados} min',
                  style: const TextStyle(
                    fontSize: 11,
                    color: Paleta.textoSecundario,
                  ),
                ),
              ),
          ],
        ),
      );
}

class _SeguimientoSinDatos extends StatelessWidget {
  const _SeguimientoSinDatos();

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: const Row(
          children: [
            Icon(Icons.local_shipping_outlined,
                size: 18, color: Paleta.textoSecundario),
            SizedBox(width: 10),
            Expanded(
              child: Text(
                'Todavía no hay información de envío para este pedido.',
                style: TextStyle(fontSize: 12, color: Paleta.textoSecundario),
              ),
            ),
          ],
        ),
      );
}

class _Fila extends StatelessWidget {
  const _Fila({required this.icono, required this.texto});
  final IconData icono;
  final String texto;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 9),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icono, size: 16, color: Paleta.textoSecundario),
            const SizedBox(width: 9),
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

class _Productos extends StatelessWidget {
  const _Productos({required this.pedido});
  final Pedido pedido;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Productos (${pedido.detalles.length})',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: Paleta.textoPrimario,
              ),
            ),
            if (pedido.detalles.isEmpty)
              const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Text(
                  'El backend no devolvió productos para este pedido.',
                  style: TextStyle(fontSize: 12, color: Paleta.textoSecundario),
                ),
              ),
            for (final detalle in pedido.detalles) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: SizedBox(
                      width: 48,
                      height: 48,
                      child: ImagenProducto(url: detalle.imagen),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          detalle.nombre,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: Paleta.textoPrimario,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          '${detalle.cantidad} × ${soles(detalle.precioUnitario)}',
                          style: const TextStyle(
                            fontSize: 11,
                            color: Paleta.textoSecundario,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    soles(detalle.total),
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: Paleta.textoPrimario,
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      );
}

class _Resumen extends StatelessWidget {
  const _Resumen({required this.pedido});
  final Pedido pedido;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Column(
          children: [
            _FilaResumen(etiqueta: 'Subtotal', valor: soles(pedido.subtotal)),
            const SizedBox(height: 8),
            _FilaResumen(
              etiqueta: 'Envío',
              valor: pedido.costoEnvio == 0 ? 'GRATIS' : soles(pedido.costoEnvio),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 10),
              child: Divider(height: 1),
            ),
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Total',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Paleta.textoPrimario,
                    ),
                  ),
                ),
                Text(
                  soles(pedido.total),
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ],
            ),
          ],
        ),
      );
}

class _FilaResumen extends StatelessWidget {
  const _FilaResumen({required this.etiqueta, required this.valor});
  final String etiqueta;
  final String? valor;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Expanded(
            child: Text(
              etiqueta,
              style: const TextStyle(fontSize: 12, color: Paleta.textoSecundario),
            ),
          ),
          Text(
            valor ?? '—',
            style: const TextStyle(fontSize: 12, color: Paleta.textoPrimario),
          ),
        ],
      );
}

class _TarjetaCargando extends StatelessWidget {
  const _TarjetaCargando({required this.titulo});
  final String titulo;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: Paleta.bordeTarjeta),
        ),
        child: Row(
          children: [
            Text(titulo, style: const TextStyle(fontSize: 13)),
            const Spacer(),
            const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          ],
        ),
      );
}

class _Mensaje extends StatelessWidget {
  const _Mensaje({required this.texto, this.alReintentar});
  final String texto;
  final VoidCallback? alReintentar;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                texto,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 12,
                  color: Paleta.textoSecundario,
                ),
              ),
              if (alReintentar != null)
                TextButton(
                  onPressed: alReintentar,
                  child: const Text('Reintentar'),
                ),
            ],
          ),
        ),
      );
}
