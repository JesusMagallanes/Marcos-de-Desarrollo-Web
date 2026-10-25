import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/categoria.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../categorias/pagina_categorias.dart';
import '../producto/pagina_producto.dart';
import 'buscador_widget.dart';

class PaginaTienda extends StatefulWidget {
  const PaginaTienda({super.key});

  @override
  State<PaginaTienda> createState() => _PaginaTiendaEstado();
}

class _PaginaTiendaEstado extends State<PaginaTienda> {
  List<Producto> _productos = const [];
  List<Categoria> _categorias = const [];
  bool _cargando = true;
  ErrorApi? _error;

  @override
  void initState() {
    super.initState();
    final catalogo = context.read<CatalogoServicio>();
    final productos = catalogo.productosEnCache;
    final categorias = catalogo.categoriasEnCache;

    if (productos != null && categorias != null) {
      _productos = productos;
      _categorias = categorias;
      _cargando = false;
    } else {
      _cargar();
    }
  }

  Future<void> _cargar({bool refrescar = false}) async {
    setState(() {
      _cargando = true;
      _error = null;
    });

    final catalogo = context.read<CatalogoServicio>();
    try {
      final resultados = await Future.wait([
        catalogo.productos(refrescar: refrescar),
        catalogo.categorias(refrescar: refrescar),
      ]);
      if (!mounted) return;
      setState(() {
        _productos = resultados[0] as List<Producto>;
        _categorias = resultados[1] as List<Categoria>;
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

  // Abre la pantalla de búsqueda
  void _abrirBuscador() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const BuscadorWidget()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(child: _construirPantallaInicio()),
    );
  }

  Widget _construirPantallaInicio() {
    final ofertas = _productos.where((p) => p.enOferta).toList();
    final top = _productos.take(8).toList();

    return RefreshIndicator(
      color: Paleta.verde,
      onRefresh: () => _cargar(refrescar: true),
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          const SliverToBoxAdapter(child: _HeaderFigma()),

          // Buscador (Botón falso que abre la pantalla)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 16.0,
                vertical: 8.0,
              ),
              child: GestureDetector(
                onTap: _abrirBuscador,
                child: Container(
                  height: 44,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Row(
                    children: [
                      SizedBox(width: 12),
                      Icon(Icons.search, color: Paleta.textoTenue, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Buscar laptops, teclados, monitores...',
                        style: TextStyle(
                          color: Paleta.textoTenue,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),

          if (_cargando)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: CircularProgressIndicator(color: Paleta.verde),
              ),
            )
          else if (_error != null)
            SliverFillRemaining(
              hasScrollBody: false,
              child: _MensajeError(
                error: _error!,
                alReintentar: () => _cargar(refrescar: true),
              ),
            )
          else
            SliverList(
              delegate: SliverChildListDelegate([
                const Padding(
                  padding: EdgeInsets.symmetric(
                    horizontal: 16.0,
                    vertical: 12.0,
                  ),
                  child: _BannerPromocional(),
                ),
                const Padding(
                  padding: EdgeInsets.symmetric(
                    horizontal: 16.0,
                    vertical: 8.0,
                  ),
                  child: Row(
                    children: [
                      _EtiquetaGarantia(
                        icono: Icons.verified_user_outlined,
                        texto: 'Garantía Oficial',
                      ),
                      SizedBox(width: 16),
                      _EtiquetaGarantia(
                        icono: Icons.local_shipping_outlined,
                        texto: 'Envío a todo el Perú',
                      ),
                    ],
                  ),
                ),
                if (ofertas.isNotEmpty) ...[
                  const _TituloSeccion(
                    titulo: 'Ofertas del día',
                    subtitulo: 'Precios rebajados por tiempo limitado',
                  ),
                  _EstanteHorizontal(productos: ofertas),
                ],
                if (top.isNotEmpty) ...[
                  _TituloSeccion(
                    titulo: 'Productos Top',
                    subtitulo: 'Lo que más se lleva la gente',
                    alPulsarVerTodos: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const PaginaCategorias(),
                      ),
                    ),
                  ),
                  _EstanteHorizontal(productos: top),
                ],
                _TituloSeccion(
                  titulo: 'Explorar catálogo',
                  subtitulo: 'Todos nuestros productos disponibles',
                  alPulsarVerTodos: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const PaginaCategorias()),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                  child: GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 16,
                          crossAxisSpacing: 16,
                          childAspectRatio: 0.60,
                        ),
                    itemCount: _productos.length,
                    itemBuilder: (_, i) {
                      final p = _productos[i];
                      return _TarjetaProductoFigma(
                        producto: p,
                        alPulsar: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => PaginaProducto(productoId: p.id),
                          ),
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 32),
              ]),
            ),
        ],
      ),
    );
  }
}

class _HeaderFigma extends StatelessWidget {
  const _HeaderFigma();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Paleta.verde,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.bolt, color: Colors.white, size: 24),
              ),
              const SizedBox(width: 12),
              const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'SmartZone',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Paleta.texto,
                    ),
                  ),
                  Text(
                    'Perú • Tecnología & Hardware',
                    style: TextStyle(fontSize: 10, color: Paleta.textoTenue),
                  ),
                ],
              ),
            ],
          ),
          Row(
            children: [
              Container(
                decoration: BoxDecoration(
                  border: Border.all(color: const Color(0xFFEEEEEE)),
                  shape: BoxShape.circle,
                ),
                child: IconButton(
                  icon: const Icon(
                    Icons.chat_bubble_outline,
                    size: 20,
                    color: Paleta.texto,
                  ),
                  onPressed: () {},
                ),
              ),
              const SizedBox(width: 8),
              Container(
                decoration: BoxDecoration(
                  border: Border.all(color: const Color(0xFFEEEEEE)),
                  shape: BoxShape.circle,
                ),
                child: IconButton(
                  icon: const Icon(
                    Icons.notifications_none_outlined,
                    size: 20,
                    color: Paleta.texto,
                  ),
                  onPressed: () {},
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TituloSeccion extends StatelessWidget {
  const _TituloSeccion({
    required this.titulo,
    this.subtitulo,
    this.alPulsarVerTodos,
  });
  final String titulo;
  final String? subtitulo;
  final VoidCallback? alPulsarVerTodos;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                titulo,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Paleta.texto,
                ),
              ),
              GestureDetector(
                onTap: alPulsarVerTodos,
                child: const Text(
                  'Ver todos',
                  style: TextStyle(
                    color: Paleta.verde,
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                  ),
                ),
              ),
            ],
          ),
          if (subtitulo != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitulo!,
              style: const TextStyle(color: Paleta.textoTenue, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }
}

class _EstanteHorizontal extends StatelessWidget {
  const _EstanteHorizontal({required this.productos});
  final List<Producto> productos;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 260,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: productos.length,
        itemBuilder: (context, index) {
          final p = productos[index];
          return Padding(
            padding: const EdgeInsets.only(right: 12.0),
            child: SizedBox(
              width: 160,
              child: _TarjetaProductoFigma(
                producto: p,
                alPulsar: () => Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => PaginaProducto(productoId: p.id),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _BannerPromocional extends StatelessWidget {
  const _BannerPromocional();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF10181A),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'GAMING SIN LÍMITES',
                  style: TextStyle(
                    color: Paleta.verde,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Nuevas RTX Serie 40',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    height: 1.1,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Ensambles listos con refrigeración líquida y garantía local.',
                  style: TextStyle(color: Colors.white70, fontSize: 12),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const PaginaCategorias(),
                      ),
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Paleta.verde,
                    foregroundColor: Colors.black,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    minimumSize: Size.zero,
                  ),
                  child: const Text(
                    'Explorar catálogo',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.memory, size: 80, color: Paleta.verde),
        ],
      ),
    );
  }
}

class _EtiquetaGarantia extends StatelessWidget {
  const _EtiquetaGarantia({required this.icono, required this.texto});
  final IconData icono;
  final String texto;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icono, size: 16, color: Paleta.verde),
        const SizedBox(width: 4),
        Text(
          texto,
          style: const TextStyle(fontSize: 12, color: Paleta.textoSuave),
        ),
      ],
    );
  }
}

class _TarjetaProductoFigma extends StatelessWidget {
  const _TarjetaProductoFigma({required this.producto, required this.alPulsar});
  final Producto producto;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) {
    final String? urlImagen = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : producto.imagenUrl;

    return GestureDetector(
      onTap: alPulsar,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFEEEEEE)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                Container(
                  height: 120,
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: const BoxDecoration(
                    color: Color(0xFFF8F9FA),
                    borderRadius: BorderRadius.vertical(
                      top: Radius.circular(16),
                    ),
                  ),
                  child: urlImagen != null && urlImagen.isNotEmpty
                      ? CachedNetworkImage(
                          imageUrl: urlImagen,
                          fit: BoxFit.contain,
                          placeholder: (_, __) => const Center(
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Paleta.verde,
                            ),
                          ),
                          errorWidget: (_, __, ___) => const Icon(
                            Icons.devices_other,
                            size: 50,
                            color: Paleta.textoTenue,
                          ),
                        )
                      : const Icon(
                          Icons.devices_other,
                          size: 50,
                          color: Paleta.textoTenue,
                        ),
                ),
                if (producto.enOferta)
                  Positioned(
                    top: 8,
                    left: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: const Text(
                        'Oferta',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                Positioned(
                  top: 8,
                  right: 8,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.favorite_border,
                      size: 16,
                      color: Paleta.textoTenue,
                    ),
                  ),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(12.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    (producto.marcaNombre ?? 'TECNOLOGÍA').toUpperCase(),
                    style: const TextStyle(
                      fontSize: 10,
                      color: Paleta.textoTenue,
                      fontWeight: FontWeight.w600,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    producto.nombre,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Paleta.texto,
                      height: 1.2,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 12, color: Colors.amber),
                      const SizedBox(width: 2),
                      Text(
                        '${(producto.calificacionPromedio ?? 0).toStringAsFixed(1)} (${producto.cantidadValoraciones})',
                        style: const TextStyle(
                          fontSize: 10,
                          color: Paleta.textoTenue,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (producto.enOferta &&
                                producto.precio > producto.precioActual)
                              Text(
                                'S/ ${producto.precio.toStringAsFixed(2)}',
                                style: const TextStyle(
                                  fontSize: 10,
                                  color: Paleta.textoTenue,
                                  decoration: TextDecoration.lineThrough,
                                ),
                              ),
                            Text(
                              'S/ ${producto.precioActual.toStringAsFixed(2)}',
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                                color: Paleta.texto,
                              ),
                            ),
                          ],
                        ),
                      ),
                      InkWell(
                        onTap: () {},
                        child: Container(
                          padding: const EdgeInsets.all(6),
                          decoration: BoxDecoration(
                            color: Paleta.verde,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.add,
                            size: 18,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MensajeError extends StatelessWidget {
  const _MensajeError({required this.error, required this.alReintentar});
  final ErrorApi error;
  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(32),
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Icon(Icons.cloud_off, size: 56, color: Paleta.textoTenue),
        const SizedBox(height: 16),
        Text(
          error.titulo,
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w700,
            color: Paleta.texto,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          error.mensaje,
          textAlign: TextAlign.center,
          style: const TextStyle(color: Paleta.textoSuave, height: 1.5),
        ),
        const SizedBox(height: 24),
        ElevatedButton.icon(
          onPressed: alReintentar,
          icon: const Icon(Icons.refresh),
          label: const Text('Reintentar'),
        ),
      ],
    ),
  );
}
