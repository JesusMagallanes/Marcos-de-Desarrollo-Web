import 'dart:async';

import 'package:flutter/material.dart';

import '../nucleo/config/tema.dart';
import '../nucleo/modelos/carrito.dart';
import '../nucleo/modelos/producto.dart';
import 'formatos.dart';
import 'tarjeta_producto.dart';

class ConfirmacionAgregar {
  static OverlayEntry? _aviso;
  static Timer? _temporizador;

  static Future<void> mostrar({
    required BuildContext context,
    required Producto producto,
    required ItemCarrito item,
    required Carrito carrito,
    required List<Producto> recomendados,
    required VoidCallback alIrAlCarrito,
  }) async {
    final panel = showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.white,
      barrierColor: Colors.black.withValues(alpha: 0.45),
      isScrollControlled: true,
      isDismissible: true,
      enableDrag: true,
      useSafeArea: true,
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.78,
      ),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (sheetContext) => _PanelConfirmacion(
        producto: producto,
        item: item,
        carrito: carrito,
        recomendados: recomendados,
        alIrAlCarrito: () {
          Navigator.of(sheetContext).pop();
          alIrAlCarrito();
        },
      ),
    );

    _mostrarAviso(context);
    await panel;
  }

  static void _mostrarAviso(BuildContext context) {
    _limpiarAviso();
    final entrada = OverlayEntry(builder: (_) => const _AvisoAgregado());
    _aviso = entrada;
    Overlay.of(context, rootOverlay: true).insert(entrada);
    _temporizador = Timer(const Duration(seconds: 3), _limpiarAviso);
  }

  static void _limpiarAviso() {
    _temporizador?.cancel();
    _temporizador = null;
    _aviso?.remove();
    _aviso = null;
  }
}

class _AvisoAgregado extends StatefulWidget {
  const _AvisoAgregado();

  @override
  State<_AvisoAgregado> createState() => _AvisoAgregadoEstado();
}

class _AvisoAgregadoEstado extends State<_AvisoAgregado>
    with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 220),
  );

  @override
  void initState() {
    super.initState();
    _control.forward();
  }

  @override
  void dispose() {
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: MediaQuery.of(context).padding.top + 12,
      left: 16,
      right: 16,
      child: IgnorePointer(
        child: FadeTransition(
          opacity: _control,
          child: SlideTransition(
            position: Tween<Offset>(
              begin: const Offset(0, -0.25),
              end: Offset.zero,
            ).animate(
              CurvedAnimation(parent: _control, curve: Curves.easeOut),
            ),
            child: Material(
              color: Colors.transparent,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  color: const Color(0xFF1F2421),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 26,
                      height: 26,
                      decoration: const BoxDecoration(
                        color: Paleta.verdeVivo,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.check,
                        size: 16,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Text(
                        'Producto agregado al carrito',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PanelConfirmacion extends StatelessWidget {
  const _PanelConfirmacion({
    required this.producto,
    required this.item,
    required this.carrito,
    required this.recomendados,
    required this.alIrAlCarrito,
  });

  final Producto producto;
  final ItemCarrito item;
  final Carrito carrito;
  final List<Producto> recomendados;
  final VoidCallback alIrAlCarrito;

  @override
  Widget build(BuildContext context) {
    final teclado = MediaQuery.of(context).viewInsets.bottom;
    final unidades = carrito.unidades;

    return SafeArea(
      top: false,
      child: Padding(
        padding: EdgeInsets.only(bottom: teclado),
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Center(
                  child: Container(
                    width: 42,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Paleta.borde,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const Center(
                  child: CircleAvatar(
                    radius: 28,
                    backgroundColor: Paleta.verdeVivo,
                    child: Icon(Icons.check, size: 30, color: Colors.white),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  '¡Listo! Se agregó a tu carrito',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: Paleta.texto,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  '$unidades ${unidades == 1 ? 'producto' : 'productos'} · Total ${soles(carrito.subtotal)}',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 13, color: Paleta.textoSuave),
                ),
                const SizedBox(height: 20),
                _TarjetaProductoAgregado(producto: producto, item: item),
                if (recomendados.isNotEmpty) ...[
                  const SizedBox(height: 22),
                  const Text(
                    'Combina con',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: Paleta.texto,
                    ),
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    height: 96,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: recomendados.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 10),
                      itemBuilder: (_, i) =>
                          _TarjetaRecomendado(producto: recomendados[i]),
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: OutlinedButton.styleFrom(
                          backgroundColor: Colors.white,
                          foregroundColor: Paleta.texto,
                          side: const BorderSide(color: Paleta.borde),
                          minimumSize: const Size.fromHeight(48),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        child: const Text('Seguir comprando'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: alIrAlCarrito,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Paleta.verde,
                          foregroundColor: Paleta.texto,
                          minimumSize: const Size.fromHeight(48),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        child: const Text('Ir al carrito'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _TarjetaProductoAgregado extends StatelessWidget {
  const _TarjetaProductoAgregado({required this.producto, required this.item});

  final Producto producto;
  final ItemCarrito item;

  @override
  Widget build(BuildContext context) {
    final imagen = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : (producto.imagenUrl ?? item.imagen);
    final caracteristicas = producto.especificaciones?.trim() ?? '';
    final marca = producto.marcaNombre?.trim() ?? '';
    final detalle = caracteristicas.isNotEmpty
        ? 'Características: $caracteristicas'
        : marca.isNotEmpty
            ? 'Marca: $marca'
            : '';

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: Paleta.borde),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              width: 84,
              height: 84,
              child: ImagenProducto(url: imagen),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (producto.categoriaNombre.trim().isNotEmpty)
                  Text(
                    producto.categoriaNombre,
                    style: const TextStyle(
                      fontSize: 11,
                      color: Paleta.textoTenue,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                const SizedBox(height: 3),
                Text(
                  producto.nombre,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Paleta.texto,
                    height: 1.25,
                  ),
                ),
                if (producto.cantidadValoraciones > 0) ...[
                  const SizedBox(height: 5),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 14, color: Color(0xFFFFC107)),
                      const SizedBox(width: 4),
                      Text(
                        (producto.calificacionPromedio ?? 0).toStringAsFixed(1),
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          color: Paleta.texto,
                        ),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        '(${producto.cantidadValoraciones})',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Paleta.textoTenue,
                        ),
                      ),
                    ],
                  ),
                ],
                const SizedBox(height: 7),
                Wrap(
                  spacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      soles(item.precio),
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                        color: Paleta.texto,
                      ),
                    ),
                    if (producto.enOferta && producto.precio > producto.precioActual)
                      Text(
                        soles(producto.precio),
                        style: const TextStyle(
                          fontSize: 12,
                          color: Paleta.textoTenue,
                          decoration: TextDecoration.lineThrough,
                        ),
                      ),
                  ],
                ),
                if (detalle.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      detalle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: Paleta.textoSuave,
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: Paleta.verdeSuave,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        'Cantidad: ${item.cantidad}',
                        style: const TextStyle(
                          fontSize: 11,
                          color: Paleta.verde,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    Text(
                      soles(item.total),
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: Paleta.textoSuave,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TarjetaRecomendado extends StatelessWidget {
  const _TarjetaRecomendado({required this.producto});

  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final imagen = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : producto.imagenUrl;

    return Container(
      width: 150,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: Paleta.borde),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SizedBox(
              width: 42,
              height: 42,
              child: ImagenProducto(url: imagen),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  producto.nombre,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: Paleta.texto,
                    height: 1.2,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  soles(producto.precioActual),
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                    color: Paleta.verde,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
