import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/estado/navegacion.dart';
import '../../nucleo/modelos/carrito.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../../nucleo/servicios/favoritos_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/confirmacion_agregar.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../login/pagina_login.dart';
import '../producto/pagina_producto.dart';

class PaginaFavoritos extends StatefulWidget {
  const PaginaFavoritos({super.key});

  @override
  State<PaginaFavoritos> createState() => _PaginaFavoritosEstado();
}

class _PaginaFavoritosEstado extends State<PaginaFavoritos> {
  late final FavoritosServicio _favoritos;
  List<Producto> _productos = const [];
  bool _cargando = true;
  String? _error;
  final Set<int> _agregando = <int>{};
  bool _confirmacionAbierta = false;

  @override
  void initState() {
    super.initState();
    _favoritos = context.read<FavoritosServicio>();
    _favoritos.addListener(_alCambiarFavoritos);
    _cargar();
  }

  @override
  void dispose() {
    _favoritos.removeListener(_alCambiarFavoritos);
    super.dispose();
  }

  Future<void> _cargar() async {
    if (!mounted) return;
    setState(() {
      _cargando = true;
      _error = null;
    });

    try {
      final catalogo = context.read<CatalogoServicio>();
      final todos = await catalogo.productos();
      if (!mounted) return;
      final porId = <int, Producto>{for (final p in todos) p.id: p};
      final ids = _favoritos.ids;
      final faltantes = ids.where((id) => !porId.containsKey(id)).toList();

      if (faltantes.isNotEmpty) {
        final resultados = await Future.wait(
          faltantes.map((id) => _productoSeguro(catalogo, id)),
        );
        for (final producto in resultados.whereType<Producto>()) {
          porId[producto.id] = producto;
        }
      }

      final visibles = ids.map((id) => porId[id]).whereType<Producto>().toList();
      if (!mounted) return;
      setState(() {
        _productos = visibles;
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e is ErrorApi
            ? e.mensajeCompleto
            : 'No pudimos cargar tus favoritos. Inténtalo de nuevo.';
        _cargando = false;
      });
    }
  }

  Future<Producto?> _productoSeguro(CatalogoServicio catalogo, int id) async {
    try {
      return await catalogo.producto(id);
    } catch (_) {
      return null;
    }
  }

  void _alCambiarFavoritos() {
    if (!mounted) return;
    final ids = _favoritos.ids;
    final actuales = <int, Producto>{for (final p in _productos) p.id: p};
    final visibles = ids.map((id) => actuales[id]).whereType<Producto>().toList();
    final nuevos = ids.where((id) => !actuales.containsKey(id)).toList();

    setState(() => _productos = visibles);
    if (nuevos.isNotEmpty && !_cargando) _cargarNuevos(nuevos);
  }

  Future<void> _cargarNuevos(List<int> ids) async {
    final catalogo = context.read<CatalogoServicio>();
    final resultados = await Future.wait(
      ids.map((id) => _productoSeguro(catalogo, id)),
    );
    if (!mounted) return;

    final porId = <int, Producto>{for (final p in _productos) p.id: p};
    for (final producto in resultados.whereType<Producto>()) {
      porId[producto.id] = producto;
    }
    final orden = _favoritos.ids;
    setState(() {
      _productos = orden.map((id) => porId[id]).whereType<Producto>().toList();
    });
  }

  void _cerrar() => favoritosDesdeNotifier.value = null;

  void _explorar() {
    favoritosDesdeNotifier.value = null;
    pestanaActivaNotifier.value = 0;
  }

  void _abrirDetalle(Producto producto) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => PaginaProducto(productoId: producto.id),
      ),
    );
  }

  Future<void> _compartir() async {
    if (_productos.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Aún no tienes favoritos para compartir.'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    final texto = StringBuffer('Mis favoritos en SmartZone\n\n');
    for (final producto in _productos) {
      texto.writeln('• ${producto.nombre} — ${soles(producto.precioActual)}');
    }
    texto.writeln('\nCompartido desde SmartZone');

    try {
      await SharePlus.instance.share(
        ShareParams(
          text: texto.toString(),
          subject: 'Mis favoritos en SmartZone',
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No pudimos compartir tus favoritos.'),
          backgroundColor: Paleta.rojo,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _agregar(Producto producto) async {
    if (_agregando.contains(producto.id) || _confirmacionAbierta) return;
    if (producto.sinStock) return;

    if (!context.read<Sesion>().autenticado) {
      final entro = await Navigator.of(context).push<bool>(
        MaterialPageRoute(
          builder: (_) => const PaginaLogin(
            motivo: 'Entra para añadir al carrito',
          ),
        ),
      );
      if (entro != true || !mounted) return;
    }

    setState(() => _agregando.add(producto.id));
    try {
      final carrito = context.read<CarritoServicio>();
      await carrito.agregar(producto.id, 1);
      if (!mounted) return;

      final error = carrito.error;
      if (error != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error.mensajeCompleto),
            backgroundColor: Paleta.rojo,
            behavior: SnackBarBehavior.floating,
          ),
        );
        return;
      }

      ItemCarrito? agregado;
      for (final linea in carrito.carrito.items) {
        if (linea.productoId == producto.id) {
          agregado = linea;
          break;
        }
      }
      if (agregado == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('No pudimos confirmar el producto agregado.'),
            backgroundColor: Paleta.rojo,
            behavior: SnackBarBehavior.floating,
          ),
        );
        return;
      }

      final recomendados =
          (context.read<CatalogoServicio>().productosEnCache ??
                  const <Producto>[])
              .where(
                (p) =>
                    p.id != producto.id &&
                    !p.sinStock &&
                    p.categoriaId == producto.categoriaId,
              )
              .take(2)
              .toList();

      _confirmacionAbierta = true;
      await ConfirmacionAgregar.mostrar(
        context: context,
        producto: producto,
        item: agregado,
        carrito: carrito.carrito,
        recomendados: recomendados,
        alIrAlCarrito: () {
          favoritosDesdeNotifier.value = null;
          pestanaActivaNotifier.value = 2;
        },
      );
    } finally {
      if (mounted) {
        setState(() {
          _agregando.remove(producto.id);
          _confirmacionAbierta = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cantidad = context.watch<FavoritosServicio>().cantidad;
    final ofertas = _productos
        .where((p) => p.enOferta && p.precio > p.precioActual)
        .length;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F8F8),
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            _encabezado(cantidad),
            if (!_cargando && _error == null && ofertas > 0) _banner(ofertas),
            Expanded(child: _contenido()),
          ],
        ),
      ),
    );
  }

  Widget _encabezado(int cantidad) => Container(
    color: Colors.white,
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
    child: SizedBox(
      height: 40,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: _BotonCuadrado(icono: Icons.arrow_back, alPulsar: _cerrar),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 48),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Mis favoritos',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14.5,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFF111714),
                  ),
                ),
                const SizedBox(height: 1),
                Text(
                  '$cantidad productos guardados',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 10.5,
                    color: Color(0xFF777777),
                  ),
                ),
              ],
            ),
          ),
          Align(
            alignment: Alignment.centerRight,
            child: _BotonCuadrado(
              icono: Icons.share_outlined,
              alPulsar: _compartir,
            ),
          ),
        ],
      ),
    ),
  );

  Widget _banner(int cantidad) => Container(
    margin: const EdgeInsets.fromLTRB(20, 8, 20, 0),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
    constraints: const BoxConstraints(minHeight: 50),
    decoration: BoxDecoration(
      color: const Color(0xFFFFF6E8),
      borderRadius: BorderRadius.circular(13),
      border: Border.all(color: const Color(0xFFFFDEAE)),
    ),
    child: Row(
      children: [
        const Icon(
          Icons.local_offer_outlined,
          size: 18,
          color: Color(0xFFC78000),
        ),
        const SizedBox(width: 9),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '$cantidad favoritos bajaron de precio',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF111714),
                ),
              ),
              const SizedBox(height: 1),
              const Text(
                'Aprovecha antes de que se agoten',
                style: TextStyle(fontSize: 10.5, color: Color(0xFF5F5F5F)),
              ),
            ],
          ),
        ),
      ],
    ),
  );

  Widget _contenido() {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_off_outlined,
                  size: 48, color: Paleta.textoTenue),
              const SizedBox(height: 12),
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Paleta.textoSuave, fontSize: 13),
              ),
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: _cargar,
                icon: const Icon(Icons.refresh, size: 18),
                label: const Text('Reintentar'),
              ),
            ],
          ),
        ),
      );
    }

    if (_productos.isEmpty) {
      return _Vacio(alExplorar: _explorar);
    }

    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 18),
      itemCount: _productos.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, indice) {
        final producto = _productos[indice];
        return _TarjetaFavorito(
          producto: producto,
          agregando: _agregando.contains(producto.id),
          bloqueado: _confirmacionAbierta,
          alAgregar: () => _agregar(producto),
          alAbrir: () => _abrirDetalle(producto),
        );
      },
    );
  }
}

class _BotonCuadrado extends StatelessWidget {
  const _BotonCuadrado({required this.icono, required this.alPulsar});

  final IconData icono;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: alPulsar,
    borderRadius: BorderRadius.circular(11),
    child: Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(11),
        border: Border.all(color: const Color(0xFFEAEAEA)),
      ),
      child: Icon(icono, size: 19, color: const Color(0xFF111714)),
    ),
  );
}

class _Vacio extends StatelessWidget {
  const _Vacio({required this.alExplorar});

  final VoidCallback alExplorar;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(
              color: Paleta.verdeSuave,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.favorite_border,
              size: 34,
              color: Paleta.verde,
            ),
          ),
          const SizedBox(height: 18),
          const Text(
            'Aún no tienes productos favoritos',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: Color(0xFF111714),
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Guarda los productos que te gusten desde el catálogo o su ficha para verlos aquí.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12.5,
              height: 1.5,
              color: Paleta.textoSuave,
            ),
          ),
          const SizedBox(height: 22),
          ElevatedButton.icon(
            onPressed: alExplorar,
            icon: const Icon(Icons.explore_outlined, size: 18),
            label: const Text('Explorar productos'),
          ),
        ],
      ),
    ),
  );
}

class _TarjetaFavorito extends StatelessWidget {
  const _TarjetaFavorito({
    required this.producto,
    required this.agregando,
    required this.bloqueado,
    required this.alAgregar,
    required this.alAbrir,
  });

  final Producto producto;
  final bool agregando;
  final bool bloqueado;
  final VoidCallback alAgregar;
  final VoidCallback alAbrir;

  @override
  Widget build(BuildContext context) {
    final url = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : producto.imagenUrl;

    return Container(
      constraints: const BoxConstraints(minHeight: 120),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFEAEAEA)),
      ),
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          GestureDetector(
            onTap: alAbrir,
            child: Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: const Color(0xFFF1F5F3),
                borderRadius: BorderRadius.circular(11),
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(11),
                child: ImagenProducto(url: url),
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 96),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    producto.categoriaNombre.toUpperCase(),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 9,
                      color: Color(0xFF777777),
                    ),
                  ),
                  const SizedBox(height: 2),
                  GestureDetector(
                    onTap: alAbrir,
                    child: Text(
                      producto.nombre,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11.5,
                        fontWeight: FontWeight.w700,
                        color: Color(0xFF111714),
                        height: 1.15,
                      ),
                    ),
                  ),
                  if (producto.cantidadValoraciones > 0) ...[
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        const Icon(
                          Icons.star,
                          size: 11,
                          color: Color(0xFFB87500),
                        ),
                        const SizedBox(width: 3),
                        Flexible(
                          child: Text(
                            '${(producto.calificacionPromedio ?? 0).toStringAsFixed(1)} (${producto.cantidadValoraciones})',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 10,
                              color: Color(0xFF777777),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                  const SizedBox(height: 3),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Flexible(
                        child: Text(
                          soles(producto.precioActual),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 14.5,
                            fontWeight: FontWeight.w800,
                            color: Color(0xFF111714),
                          ),
                        ),
                      ),
                      if (producto.enOferta &&
                          producto.precio > producto.precioActual) ...[
                        const SizedBox(width: 6),
                        Flexible(
                          child: Text(
                            soles(producto.precio),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 10,
                              color: Color(0xFF777777),
                              decoration: TextDecoration.lineThrough,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const Spacer(),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      Expanded(child: _estado(producto)),
                      const SizedBox(width: 6),
                      _botonAgregar(),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _estado(Producto p) {
    if (p.sinStock) {
      return const Text(
        'Sin stock',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 10.5,
          fontWeight: FontWeight.w700,
          color: Paleta.rojo,
        ),
      );
    }
    if (p.enOferta && p.precio > p.precioActual) {
      return Text(
        'Bajó ${soles(p.precio - p.precioActual)}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(
          fontSize: 10.5,
          fontWeight: FontWeight.w700,
          color: Color(0xFFC31919),
        ),
      );
    }
    if (p.stock <= 5) {
      return Text(
        'Quedan ${p.stock}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(
          fontSize: 10.5,
          fontWeight: FontWeight.w700,
          color: Color(0xFFB36B00),
        ),
      );
    }
    return const Text(
      'En stock',
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(
        fontSize: 10.5,
        fontWeight: FontWeight.w700,
        color: Color(0xFF078A00),
      ),
    );
  }

  Widget _botonAgregar() {
    final deshabilitado = producto.sinStock || agregando || bloqueado;
    return Opacity(
      opacity: deshabilitado ? 0.55 : 1,
      child: InkWell(
        onTap: deshabilitado ? null : alAgregar,
        borderRadius: BorderRadius.circular(9),
        child: Container(
          height: 29,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            color: const Color(0xFF00ED8A),
            borderRadius: BorderRadius.circular(9),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.shopping_cart,
                size: 14,
                color: Color(0xFF111714),
              ),
              SizedBox(width: 5),
              Text(
                'Agregar',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: Color(0xFF111714),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
