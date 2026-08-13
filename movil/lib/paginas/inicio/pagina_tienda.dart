import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/categoria.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../../widgets/tarjeta_producto.dart';
import '../producto/pagina_producto.dart';

/// La tienda: buscador, categorías y rejilla de productos.
///
/// Los datos son exactamente los mismos que ve la web, del mismo backend: lo
/// que el administrador publique desde el panel aparece aquí sin más.
class PaginaTienda extends StatefulWidget {
  const PaginaTienda({super.key});

  @override
  State<PaginaTienda> createState() => _PaginaTiendaEstado();
}

class _PaginaTiendaEstado extends State<PaginaTienda> {
  final _buscador = TextEditingController();
  Timer? _reboteBusqueda;

  List<Producto> _productos = const [];
  List<Categoria> _categorias = const [];
  int? _categoriaElegida;

  bool _cargando = true;
  ErrorApi? _error;

  @override
  void initState() {
    super.initState();

    // Si el arranque ya precargó el catálogo, se pinta de inmediato. Pasar por
    // el Future aunque el dato esté en memoria costaría un frame de spinner
    // nada más levantarse la pantalla de carga.
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

  @override
  void dispose() {
    _reboteBusqueda?.cancel();
    _buscador.dispose();
    super.dispose();
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

  /// Se espera medio segundo desde la última tecla: sin esto se dispara una
  /// petición por cada letra, y en móvil la red no está para desperdiciarla.
  void _buscarConRebote(String texto) {
    _reboteBusqueda?.cancel();
    _reboteBusqueda = Timer(const Duration(milliseconds: 500), () => _buscar(texto));
  }

  Future<void> _buscar(String texto) async {
    final catalogo = context.read<CatalogoServicio>();
    setState(() {
      _cargando = true;
      _error = null;
      _categoriaElegida = null;
    });
    try {
      final encontrados = await catalogo.productos(buscar: texto);
      if (!mounted) return;
      setState(() {
        _productos = encontrados;
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

  List<Producto> get _visibles => _categoriaElegida == null
      ? _productos
      : _productos.where((p) => p.categoriaId == _categoriaElegida).toList();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('SmartZone'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(60),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: TextField(
              controller: _buscador,
              onChanged: _buscarConRebote,
              textInputAction: TextInputAction.search,
              onSubmitted: _buscar,
              decoration: InputDecoration(
                hintText: '¿Qué estás buscando?',
                prefixIcon: const Icon(Icons.search, color: Paleta.textoTenue),
                suffixIcon: _buscador.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close, size: 20),
                        onPressed: () {
                          _buscador.clear();
                          _cargar();
                        },
                      ),
                isDense: true,
              ),
            ),
          ),
        ),
      ),
      body: RefreshIndicator(
        color: Paleta.verde,
        onRefresh: () => _cargar(refrescar: true),
        child: _construirCuerpo(),
      ),
    );
  }

  Widget _construirCuerpo() {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return _MensajeError(error: _error!, alReintentar: () => _cargar(refrescar: true));
    }

    return CustomScrollView(
      // `always` para que se pueda tirar a refrescar aunque quepa todo.
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        if (_categorias.isNotEmpty)
          SliverToBoxAdapter(
            child: SizedBox(
              height: 44,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                itemCount: _categorias.length + 1,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (_, i) {
                  if (i == 0) {
                    return _Filtro(
                      etiqueta: 'Todo',
                      activo: _categoriaElegida == null,
                      alPulsar: () => setState(() => _categoriaElegida = null),
                    );
                  }
                  final c = _categorias[i - 1];
                  return _Filtro(
                    etiqueta: c.nombre,
                    activo: _categoriaElegida == c.id,
                    alPulsar: () => setState(() => _categoriaElegida = c.id),
                  );
                },
              ),
            ),
          ),
        if (_visibles.isEmpty)
          const SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text(
                  'No encontramos productos con esa búsqueda.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Paleta.textoSuave),
                ),
              ),
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: SliverGrid(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                childAspectRatio: 0.62,
              ),
              delegate: SliverChildBuilderDelegate(
                (_, i) {
                  final p = _visibles[i];
                  return TarjetaProducto(
                    producto: p,
                    alPulsar: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => PaginaProducto(productoId: p.id)),
                    ),
                  );
                },
                childCount: _visibles.length,
              ),
            ),
          ),
      ],
    );
  }
}

class _Filtro extends StatelessWidget {
  const _Filtro({required this.etiqueta, required this.activo, required this.alPulsar});
  final String etiqueta;
  final bool activo;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => ChoiceChip(
        label: Text(etiqueta),
        selected: activo,
        onSelected: (_) => alPulsar(),
        showCheckmark: false,
        backgroundColor: Colors.white,
        selectedColor: Paleta.verdeSuave,
        side: BorderSide(color: activo ? Paleta.verde : Paleta.borde),
        labelStyle: TextStyle(
          color: activo ? Paleta.verde : Paleta.textoSuave,
          fontWeight: activo ? FontWeight.w600 : FontWeight.w500,
          fontSize: 13,
        ),
      );
}

/// Mensaje de error con reintento. Se separa porque el mismo formato sirve en
/// la tienda, en el detalle y en el carrito.
class _MensajeError extends StatelessWidget {
  const _MensajeError({required this.error, required this.alReintentar});
  final ErrorApi error;
  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(32),
        children: [
          const SizedBox(height: 60),
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
          Center(
            child: ElevatedButton.icon(
              onPressed: alReintentar,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ),
        ],
      );
}
