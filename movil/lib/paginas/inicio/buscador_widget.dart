import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/categoria.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../producto/pagina_producto.dart';

class BuscadorWidget extends StatefulWidget {
  const BuscadorWidget({super.key});

  @override
  State<BuscadorWidget> createState() => _BuscadorWidgetState();
}

class _BuscadorWidgetState extends State<BuscadorWidget> {
  final _buscador = TextEditingController();
  final _focusNodo = FocusNode();
  Timer? _reboteBusqueda;
  String _textoBusqueda = "";

  List<Producto> _resultados = [];
  List<String> _sugerencias = [];
  List<String> _busquedasRecientes = [
    'laptop gamer',
    'monitor 144hz',
    'teclado switch red',
  ];
  bool _cargando = false;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _focusNodo.requestFocus(),
    );
  }

  @override
  void dispose() {
    _reboteBusqueda?.cancel();
    _buscador.dispose();
    _focusNodo.dispose();
    super.dispose();
  }

  void _onSearchChanged(String texto) {
    _reboteBusqueda?.cancel();
    setState(() => _textoBusqueda = texto);

    if (texto.trim().isEmpty) {
      setState(() {
        _sugerencias = [];
        _resultados = [];
      });
      return;
    }

    _generarSugerencias(texto);

    _reboteBusqueda = Timer(
      const Duration(milliseconds: 400),
      () => _buscarEnBackend(texto),
    );
  }

  void _generarSugerencias(String texto) {
    final query = texto.toLowerCase();
    final Set<String> nuevasSugerencias = {};
    final catalogo = context.read<CatalogoServicio>();
    final productos = catalogo.productosEnCache ?? [];
    final categorias = catalogo.categoriasEnCache ?? [];

    for (var p in productos) {
      if (p.nombre.toLowerCase().contains(query)) {
        nuevasSugerencias.add(p.nombre.toLowerCase());
      }
      if (nuevasSugerencias.length >= 3) break;
    }
    for (var c in categorias) {
      if (c.nombre.toLowerCase().contains(query)) {
        nuevasSugerencias.add(c.nombre.toLowerCase());
      }
    }

    setState(() => _sugerencias = nuevasSugerencias.toList());
  }

  Future<void> _buscarEnBackend(String texto) async {
    final catalogo = context.read<CatalogoServicio>();
    setState(() => _cargando = true);
    try {
      final encontrados = await catalogo.productos(buscar: texto);
      if (!mounted) return;
      setState(() {
        _resultados = encontrados;
        _cargando = false;
      });
    } catch (e) {
      setState(() => _cargando = false);
    }
  }

  void _seleccionarTexto(String texto) {
    _buscador.text = texto;
    _focusNodo.unfocus();
    _onSearchChanged(texto);
    if (!_busquedasRecientes.contains(texto)) {
      setState(() => _busquedasRecientes.insert(0, texto));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new,
            size: 20,
            color: Paleta.texto,
          ),
          onPressed: () => Navigator.pop(context),
        ),
        title: Container(
          height: 44,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFFEEEEEE)),
          ),
          child: TextField(
            controller: _buscador,
            focusNode: _focusNodo,
            onChanged: _onSearchChanged,
            decoration: InputDecoration(
              hintText: 'Buscar laptops, teclados...',
              hintStyle: const TextStyle(
                color: Paleta.textoTenue,
                fontSize: 14,
              ),
              prefixIcon: const Icon(
                Icons.search,
                size: 20,
                color: Paleta.textoTenue,
              ),
              suffixIcon: _textoBusqueda.isNotEmpty
                  ? IconButton(
                      icon: const Icon(
                        Icons.clear,
                        size: 18,
                        color: Paleta.textoTenue,
                      ),
                      onPressed: () {
                        _buscador.clear();
                        _onSearchChanged('');
                      },
                    )
                  : null,
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(vertical: 12),
            ),
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Historial
          if (_textoBusqueda.isEmpty && _busquedasRecientes.isNotEmpty) ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Búsquedas recientes',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: Paleta.texto,
                  ),
                ),
                TextButton(
                  onPressed: () => setState(() => _busquedasRecientes.clear()),
                  child: const Text(
                    'Borrar',
                    style: TextStyle(color: Paleta.verde, fontSize: 13),
                  ),
                ),
              ],
            ),
            ..._busquedasRecientes.map(
              (termino) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(
                  Icons.history,
                  color: Paleta.textoTenue,
                  size: 20,
                ),
                title: Text(
                  termino,
                  style: const TextStyle(fontSize: 14, color: Paleta.texto),
                ),
                trailing: const Icon(
                  Icons.north_west,
                  color: Paleta.textoTenue,
                  size: 16,
                ),
                onTap: () => _seleccionarTexto(termino),
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Sugerencias
          if (_sugerencias.isNotEmpty) ...[
            const Text(
              'Sugerencias',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 15,
                color: Paleta.texto,
              ),
            ),
            const SizedBox(height: 8),
            ..._sugerencias.map(
              (sugerencia) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(
                  Icons.search,
                  color: Paleta.textoTenue,
                  size: 20,
                ),
                title: Text(
                  sugerencia,
                  style: const TextStyle(fontSize: 14, color: Paleta.texto),
                ),
                onTap: () => _seleccionarTexto(sugerencia),
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Resultados
          if (_textoBusqueda.isNotEmpty) ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Resultados rápidos',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                    color: Paleta.texto,
                  ),
                ),
                Text(
                  '${_resultados.length} encontrados',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Paleta.textoTenue,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (_cargando)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(32),
                  child: CircularProgressIndicator(color: Paleta.verde),
                ),
              )
            else if (_resultados.isEmpty)
              const Padding(
                padding: EdgeInsets.all(32),
                child: Center(
                  child: Text(
                    'No hay resultados',
                    style: TextStyle(color: Paleta.textoSuave),
                  ),
                ),
              )
            else
              ..._resultados.map(
                (p) => Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _TarjetaBusquedaHorizontal(producto: p),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _TarjetaBusquedaHorizontal extends StatelessWidget {
  const _TarjetaBusquedaHorizontal({required this.producto});
  final Producto producto;

  @override
  Widget build(BuildContext context) {
    final String? urlImagen = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : producto.imagenUrl;

    return GestureDetector(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => PaginaProducto(productoId: producto.id),
        ),
      ),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFEEEEEE)),
        ),
        child: Row(
          children: [
            Container(
              width: 60,
              height: 60,
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFFF8F9FA),
                borderRadius: BorderRadius.circular(8),
              ),
              child: urlImagen != null && urlImagen.isNotEmpty
                  ? CachedNetworkImage(imageUrl: urlImagen, fit: BoxFit.contain)
                  : const Icon(Icons.devices_other, color: Paleta.textoTenue),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    (producto.marcaNombre ?? 'TECNOLOGÍA').toUpperCase(),
                    style: const TextStyle(
                      fontSize: 9,
                      color: Paleta.textoTenue,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    producto.nombre,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Paleta.texto,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 10, color: Colors.amber),
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
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Text(
                        'S/ ${producto.precioActual.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: Paleta.texto,
                        ),
                      ),
                      if (producto.enOferta &&
                          producto.precio > producto.precioActual) ...[
                        const SizedBox(width: 6),
                        Text(
                          'S/ ${producto.precio.toStringAsFixed(2)}',
                          style: const TextStyle(
                            fontSize: 11,
                            color: Paleta.textoTenue,
                            decoration: TextDecoration.lineThrough,
                          ),
                        ),
                      ],
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
