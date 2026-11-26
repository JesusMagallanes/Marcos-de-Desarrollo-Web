import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/pedido.dart';
import '../../nucleo/servicios/compras_servicio.dart';
import '../../widgets/cabecera_seccion.dart';
import '../../widgets/chip_estado.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../producto/pagina_producto.dart';
import 'pagina_detalle_pedido.dart';

/// Listado real de compras del usuario autenticado.
class PaginaPedidos extends StatefulWidget {
  const PaginaPedidos({super.key});

  @override
  State<PaginaPedidos> createState() => _PaginaPedidosEstado();
}

class _PaginaPedidosEstado extends State<PaginaPedidos> {
  late final Future<List<Pedido>> _pedidos;
  String _filtro = 'Todos';
  bool _buscando = false;
  final _busqueda = TextEditingController();

  @override
  void initState() {
    super.initState();
    _pedidos = context.read<ComprasServicio>().misPedidos();
  }

  @override
  void dispose() {
    _busqueda.dispose();
    super.dispose();
  }

  void _recargar() => setState(() {
        _pedidos = context.read<ComprasServicio>().misPedidos();
      });

  List<Pedido> _filtrar(List<Pedido> pedidos) {
    var resultado = pedidos;
    if (_filtro == 'En curso') {
      resultado = resultado.where((p) => p.enCurso).toList();
    } else if (_filtro == 'Entregados') {
      resultado = resultado.where((p) => p.entregado).toList();
    }

    final consulta = _busqueda.text.trim().toLowerCase();
    if (consulta.isNotEmpty) {
      resultado = resultado.where((p) {
        final coincideNumero = p.numero.toLowerCase().contains(consulta);
        final coincideProducto = p.detalles.any(
          (d) => d.nombre.toLowerCase().contains(consulta),
        );
        return coincideNumero || coincideProducto;
      }).toList();
    }
    return resultado;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8F8F8),
        body: Column(
          children: [
            CabeceraSeccion(
              titulo: 'Mis pedidos',
              alVolver: () => Navigator.of(context).maybePop(),
              accion: IconButton(
                onPressed: () => setState(() => _buscando = !_buscando),
                icon: const Icon(Icons.search, size: 20),
                color: Paleta.textoPrimario,
              ),
            ),
            if (_buscando)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
                child: TextField(
                  controller: _busqueda,
                  onChanged: (_) => setState(() {}),
                  decoration: InputDecoration(
                    hintText: 'Buscar por número o producto',
                    prefixIcon: const Icon(Icons.search, size: 18),
                    filled: true,
                    fillColor: Colors.white,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Paleta.bordeTarjeta),
                    ),
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: Row(
                children: [
                  for (final filtro in ['Todos', 'En curso', 'Entregados']) ...[
                    _ChipFiltro(
                      etiqueta: filtro,
                      activo: _filtro == filtro,
                      alPulsar: () => setState(() => _filtro = filtro),
                    ),
                    const SizedBox(width: 6),
                  ],
                ],
              ),
            ),
            Expanded(
              child: FutureBuilder<List<Pedido>>(
                future: _pedidos,
                builder: (context, estado) {
                  if (estado.connectionState == ConnectionState.waiting) {
                    return const Center(
                      child: CircularProgressIndicator(strokeWidth: 2),
                    );
                  }
                  if (estado.hasError) {
                    final error = estado.error;
                    return _Mensaje(
                      texto: error is ErrorApi
                          ? error.mensaje
                          : 'No pudimos cargar tus pedidos.',
                      alReintentar: _recargar,
                    );
                  }
                  final pedidos = _filtrar(estado.data ?? const <Pedido>[]);
                  if (pedidos.isEmpty) {
                    return const _Mensaje(
                      texto: 'No hay pedidos para este filtro.',
                    );
                  }
                  return ListView.separated(
                    padding: const EdgeInsets.fromLTRB(15, 8, 15, 16),
                    itemCount: pedidos.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (_, i) => _TarjetaPedido(pedido: pedidos[i]),
                  );
                },
              ),
            ),
          ],
        ),
      );

}

class _ChipFiltro extends StatelessWidget {
  const _ChipFiltro({
    required this.etiqueta,
    required this.activo,
    required this.alPulsar,
  });

  final String etiqueta;
  final bool activo;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: alPulsar,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(
            color: activo ? Paleta.negroTarjeta : Colors.white,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: activo ? Paleta.negroTarjeta : Paleta.bordeTarjeta,
            ),
          ),
          child: Text(
            etiqueta,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: activo ? Colors.white : Paleta.textoSecundario,
            ),
          ),
        ),
      );
}

class _TarjetaPedido extends StatelessWidget {
  const _TarjetaPedido({required this.pedido});
  final Pedido pedido;

  @override
  Widget build(BuildContext context) {
    final detalle = pedido.detalles.isNotEmpty ? pedido.detalles.first : null;
    final fecha = pedido.fecha == null
        ? ''
        : DateFormat('d MMM', 'es_PE').format(pedido.fecha!.toLocal());

    return Container(
      padding: const EdgeInsets.all(12),
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
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ),
              ChipEstado(
                etiqueta: pedido.etiquetaEstado,
                fondo: _colorEstado(pedido.estado),
                colorTexto: pedido.estado == 'EN_TRANSITO'
                    ? Paleta.textoPrimario
                    : pedido.estado == 'CANCELADO'
                        ? Paleta.rojo
                        : Paleta.verdeOscuro,
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 45,
                  height: 45,
                  child: ImagenProducto(url: detalle?.imagen),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      detalle == null
                          ? 'Pedido sin productos'
                          : '${detalle.cantidad}x ${detalle.nombre}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: Paleta.textoPrimario,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      fecha.isEmpty ? pedido.metodoPago : '$fecha · ${pedido.metodoPago}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: Paleta.textoSecundario,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 10),
            child: Divider(height: 1),
          ),
          Row(
            children: [
              TextButton(
                onPressed: () => pedido.entregado
                    ? _volverAComprarDesde(context, pedido)
                    : _abrirDetalleDesde(context, pedido),
                style: TextButton.styleFrom(
                  padding: EdgeInsets.zero,
                  minimumSize: const Size(0, 28),
                ),
                child: Text(
                  pedido.entregado ? 'Volver a comprar' : 'Seguir pedido',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: Paleta.verdeOscuro,
                  ),
                ),
              ),
              const Spacer(),
              Text(
                soles(pedido.total),
                style: const TextStyle(
                  fontSize: 13,
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

  void _abrirDetalleDesde(BuildContext context, Pedido pedido) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PaginaDetallePedido(pedidoId: pedido.id),
      ),
    );
  }

  void _volverAComprarDesde(BuildContext context, Pedido pedido) {
    final productoId = pedido.detalles.isNotEmpty ? pedido.detalles.first.productoId : null;
    if (productoId == null) return;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PaginaProducto(productoId: productoId),
      ),
    );
  }

  Color _colorEstado(String estado) {
    switch (estado) {
      case 'EN_TRANSITO':
        return const Color(0xFFFDE8D0);
      case 'CANCELADO':
        return const Color(0xFFFFE7E7);
      case 'ENTREGADO':
        return Paleta.verdeSuave;
      default:
        return Paleta.verdeSuave;
    }
  }
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
