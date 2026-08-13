import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../login/pagina_login.dart';

/// Ficha del producto con su galería, precio, stock y el botón de añadir.
class PaginaProducto extends StatefulWidget {
  const PaginaProducto({super.key, required this.productoId});
  final int productoId;

  @override
  State<PaginaProducto> createState() => _PaginaProductoEstado();
}

class _PaginaProductoEstado extends State<PaginaProducto> {
  Producto? _producto;
  ErrorApi? _error;
  bool _cargando = true;
  int _imagenActual = 0;
  int _cantidad = 1;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final p = await context.read<CatalogoServicio>().producto(widget.productoId);
      if (!mounted) return;
      setState(() {
        _producto = p;
        _cargando = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _cargando = false;
      });
    }
  }

  Future<void> _agregar() async {
    final sesion = context.read<Sesion>();

    // El carrito vive en el servidor y va atado al usuario del token, así que
    // sin sesión no hay dónde guardarlo: se pide entrar y, si entra, se
    // continúa con lo que estaba haciendo.
    if (!sesion.autenticado) {
      final entro = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => const PaginaLogin(motivo: 'Entra para añadir al carrito')),
      );
      if (entro != true || !mounted) return;
    }

    final carrito = context.read<CarritoServicio>();
    await carrito.agregar(widget.productoId, _cantidad);
    if (!mounted) return;

    final error = carrito.error;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(error?.mensajeCompleto ?? 'Añadido al carrito'),
        backgroundColor: error != null ? Paleta.rojo : Paleta.verde,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_cargando) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (_error != null || _producto == null) {
      return Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.search_off, size: 56, color: Paleta.textoTenue),
                const SizedBox(height: 16),
                Text(
                  _error?.mensaje ?? 'No encontramos este producto.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Paleta.textoSuave),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final p = _producto!;
    final galeria = p.imagenes.isNotEmpty ? p.imagenes : [if (p.imagenUrl != null) p.imagenUrl!];

    return Scaffold(
      appBar: AppBar(title: Text(p.categoriaNombre)),
      body: ListView(
        children: [
          // Galería
          ColoredBox(
            color: Colors.white,
            child: Column(
              children: [
                SizedBox(
                  height: 300,
                  child: galeria.isEmpty
                      ? const ImagenProducto(url: null)
                      : PageView.builder(
                          itemCount: galeria.length,
                          onPageChanged: (i) => setState(() => _imagenActual = i),
                          itemBuilder: (_, i) => Padding(
                            padding: const EdgeInsets.all(16),
                            child: ImagenProducto(url: galeria[i]),
                          ),
                        ),
                ),
                if (galeria.length > 1)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: List.generate(
                        galeria.length,
                        (i) => AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          width: i == _imagenActual ? 18 : 6,
                          height: 6,
                          decoration: BoxDecoration(
                            color: i == _imagenActual ? Paleta.verde : Paleta.borde,
                            borderRadius: BorderRadius.circular(3),
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),

          Container(
            color: Colors.white,
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            margin: const EdgeInsets.only(top: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (p.marcaNombre != null)
                  Text(
                    p.marcaNombre!.toUpperCase(),
                    style: const TextStyle(
                      fontSize: 11,
                      color: Paleta.textoTenue,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.5,
                    ),
                  ),
                const SizedBox(height: 6),
                Text(
                  p.nombre,
                  style: const TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w700,
                    color: Paleta.texto,
                    height: 1.3,
                  ),
                ),

                if (p.cantidadValoraciones > 0) ...[
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 16, color: Color(0xFFFFC107)),
                      const SizedBox(width: 4),
                      Text(
                        (p.calificacionPromedio ?? 0).toStringAsFixed(1),
                        style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        '(${p.cantidadValoraciones})',
                        style: const TextStyle(color: Paleta.textoTenue, fontSize: 13),
                      ),
                    ],
                  ),
                ],

                const SizedBox(height: 14),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      soles(p.precioActual),
                      style: const TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                        color: Paleta.texto,
                      ),
                    ),
                    if (p.enOferta && p.precio > p.precioActual) ...[
                      const SizedBox(width: 10),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Text(
                          soles(p.precio),
                          style: const TextStyle(
                            fontSize: 15,
                            color: Paleta.textoTenue,
                            decoration: TextDecoration.lineThrough,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),

                const SizedBox(height: 10),
                Row(
                  children: [
                    Icon(
                      p.sinStock ? Icons.cancel_outlined : Icons.check_circle_outline,
                      size: 17,
                      color: p.sinStock ? Paleta.rojo : Paleta.verde,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      p.sinStock ? 'Sin stock' : 'Disponible (${p.stock})',
                      style: TextStyle(
                        color: p.sinStock ? Paleta.rojo : Paleta.verde,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 20),
                const Text(
                  'Descripción',
                  style: TextStyle(fontWeight: FontWeight.w700, color: Paleta.texto),
                ),
                const SizedBox(height: 6),
                Text(
                  p.descripcion,
                  style: const TextStyle(color: Paleta.textoSuave, height: 1.6, fontSize: 14),
                ),

                if ((p.especificaciones ?? '').trim().isNotEmpty) ...[
                  const SizedBox(height: 20),
                  const Text(
                    'Especificaciones',
                    style: TextStyle(fontWeight: FontWeight.w700, color: Paleta.texto),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    p.especificaciones!,
                    style: const TextStyle(color: Paleta.textoSuave, height: 1.6, fontSize: 14),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),

      bottomNavigationBar: p.sinStock
          ? null
          : SafeArea(
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  border: Border(top: BorderSide(color: Paleta.borde)),
                ),
                child: Row(
                  children: [
                    _SelectorCantidad(
                      cantidad: _cantidad,
                      maximo: p.stock,
                      alCambiar: (n) => setState(() => _cantidad = n),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: context.watch<CarritoServicio>().cargando ? null : _agregar,
                        icon: const Icon(Icons.add_shopping_cart, size: 20),
                        label: const Text('Agregar al carrito'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class _SelectorCantidad extends StatelessWidget {
  const _SelectorCantidad({
    required this.cantidad,
    required this.maximo,
    required this.alCambiar,
  });

  final int cantidad;
  final int maximo;
  final ValueChanged<int> alCambiar;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: Paleta.borde),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            visualDensity: VisualDensity.compact,
            // Deshabilitado en el mínimo en vez de dejar bajar a cero: quitar
            // del carrito es otra acción, y aquí solo se elige cuánto llevar.
            onPressed: cantidad > 1 ? () => alCambiar(cantidad - 1) : null,
            icon: const Icon(Icons.remove, size: 18),
          ),
          Text('$cantidad', style: const TextStyle(fontWeight: FontWeight.w700)),
          IconButton(
            visualDensity: VisualDensity.compact,
            onPressed: cantidad < maximo ? () => alCambiar(cantidad + 1) : null,
            icon: const Icon(Icons.add, size: 18),
          ),
        ],
      ),
    );
  }
}
