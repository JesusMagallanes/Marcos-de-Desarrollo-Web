import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/categoria.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../producto/pagina_producto.dart';
import '../inicio/buscador_widget.dart';

class PaginaCategoriaProductos extends StatefulWidget {
  final Categoria? categoria;
  final String? marca;

  const PaginaCategoriaProductos({super.key, this.categoria, this.marca})
    : assert(
        categoria != null || marca != null,
        'Debes pasar una categoría o una marca',
      );

  @override
  State<PaginaCategoriaProductos> createState() =>
      _PaginaCategoriaProductosState();
}

class _PaginaCategoriaProductosState extends State<PaginaCategoriaProductos> {
  List<Producto> _productosOriginales = [];
  List<Producto> _productosFiltrados = [];
  bool _cargando = true;

  // Variables de los filtros
  RangeValues _rangoPrecio = const RangeValues(0, 7000);
  String _ordenSeleccionado = 'Relevancia';

  // Estos se llenarán dinámicamente según la categoría
  String _atributoSeleccionado = 'Todos';
  String _tipoAtributo = '';

  String get _titulo => widget.categoria?.nombre ?? widget.marca ?? 'Productos';

  @override
  void initState() {
    super.initState();
    _cargarProductos();
  }

  Future<void> _cargarProductos() async {
    final catalogo = context.read<CatalogoServicio>();
    try {
      final todos = catalogo.productosEnCache ?? await catalogo.productos();

      setState(() {
        if (widget.categoria != null) {
          _productosOriginales = todos
              .where((p) => p.categoriaId == widget.categoria!.id)
              .toList();
        } else if (widget.marca != null) {
          _productosOriginales = todos
              .where(
                (p) =>
                    p.marcaNombre?.toLowerCase() == widget.marca!.toLowerCase(),
              )
              .toList();
        }

        _productosFiltrados = List.from(_productosOriginales);
        _cargando = false;
      });
    } catch (e) {
      setState(() => _cargando = false);
    }
  }

  void _aplicarFiltros() {
    List<Producto> filtrados = List.from(_productosOriginales);

    // Filtrar por Precio
    filtrados = filtrados
        .where(
          (p) =>
              p.precioActual >= _rangoPrecio.start &&
              p.precioActual <= _rangoPrecio.end,
        )
        .toList();

    // Filtrar por el atributo dinámico (ej: RTX, Inalámbrico, etc)
    if (_atributoSeleccionado != 'Todos' && _tipoAtributo.isNotEmpty) {
      filtrados = filtrados.where((p) {
        final texto = '${p.nombre} ${p.especificaciones ?? ''}'.toLowerCase();
        return texto.contains(_atributoSeleccionado.toLowerCase());
      }).toList();
    }

    // Ordenar
    switch (_ordenSeleccionado) {
      case 'Menor precio':
        filtrados.sort((a, b) => a.precioActual.compareTo(b.precioActual));
        break;
      case 'Mayor precio':
        filtrados.sort((a, b) => b.precioActual.compareTo(a.precioActual));
        break;
      case 'Mejor calificados':
        filtrados.sort(
          (a, b) => (b.calificacionPromedio ?? 0).compareTo(
            a.calificacionPromedio ?? 0,
          ),
        );
        break;
    }

    setState(() {
      _productosFiltrados = filtrados;
    });
  }

  // Detecta qué filtros mostrar según la categoría
  Map<String, List<String>> _obtenerFiltrosPorCategoria() {
    String cat = _titulo.toLowerCase();
    if (cat.contains('laptop') || cat.contains('pc')) {
      _tipoAtributo = 'Tarjeta gráfica';
      return {
        'Tarjeta gráfica': ['RTX 4060', 'RTX 4070', 'RTX 4080', 'Integrada'],
        'Memoria RAM': ['8 GB', '16 GB', '32 GB'],
      };
    } else if (cat.contains('mouse') || cat.contains('teclado')) {
      _tipoAtributo = 'Conectividad';
      return {
        'Conectividad': ['Inalámbrico', 'Cable', 'Bluetooth'],
        'Tipo': ['Gamer', 'Oficina'],
      };
    } else if (cat.contains('monitor')) {
      _tipoAtributo = 'Tasa de refresco';
      return {
        'Tasa de refresco': ['60Hz', '144Hz', '165Hz', '240Hz'],
        'Resolución': ['1080p', '2K', '4K'],
      };
    } else if (cat.contains('disco') || cat.contains('ssd')) {
      _tipoAtributo = 'Capacidad';
      return {
        'Capacidad': ['500 GB', '1 TB', '2 TB'],
        'Tipo': ['SSD', 'HDD', 'NVMe'],
      };
    } else {
      _tipoAtributo = 'Características';
      return {
        'Características': ['Gamer', 'Oficina', 'Inalámbrico'],
      };
    }
  }

  void _abrirModalFiltros() {
    RangeValues tempRango = _rangoPrecio;
    String tempOrden = _ordenSeleccionado;
    String tempAtributo = _atributoSeleccionado;

    Map<String, List<String>> filtrosDisponibles =
        _obtenerFiltrosPorCategoria();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) {
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setModalState) {
            // Calculamos el número de resultados en tiempo real
            List<Producto> tempLista = List.from(_productosOriginales);
            tempLista = tempLista
                .where(
                  (p) =>
                      p.precioActual >= tempRango.start &&
                      p.precioActual <= tempRango.end,
                )
                .toList();
            if (tempAtributo != 'Todos') {
              tempLista = tempLista
                  .where(
                    (p) => '${p.nombre} ${p.especificaciones ?? ''}'
                        .toLowerCase()
                        .contains(tempAtributo.toLowerCase()),
                  )
                  .toList();
            }

            return Padding(
              padding: EdgeInsets.only(
                bottom: MediaQuery.of(context).viewInsets.bottom,
                left: 20,
                right: 20,
                top: 20,
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text(
                          'Filtrar y ordenar',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        TextButton(
                          onPressed: () {
                            setModalState(() {
                              tempRango = const RangeValues(0, 7000);
                              tempOrden = 'Relevancia';
                              tempAtributo = 'Todos';
                            });
                          },
                          child: const Text(
                            'Limpiar',
                            style: TextStyle(color: Paleta.verde),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // ORDENAR POR
                    const Text(
                      'Ordenar por',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                    _OpcionRadio(
                      texto: 'Relevancia',
                      seleccionado: tempOrden == 'Relevancia',
                      onTap: () =>
                          setModalState(() => tempOrden = 'Relevancia'),
                    ),
                    _OpcionRadio(
                      texto: 'Menor precio',
                      seleccionado: tempOrden == 'Menor precio',
                      onTap: () =>
                          setModalState(() => tempOrden = 'Menor precio'),
                    ),
                    _OpcionRadio(
                      texto: 'Mayor precio',
                      seleccionado: tempOrden == 'Mayor precio',
                      onTap: () =>
                          setModalState(() => tempOrden = 'Mayor precio'),
                    ),
                    _OpcionRadio(
                      texto: 'Mejor calificados',
                      seleccionado: tempOrden == 'Mejor calificados',
                      onTap: () =>
                          setModalState(() => tempOrden = 'Mejor calificados'),
                    ),

                    const SizedBox(height: 16),
                    const Text(
                      'Rango de precio',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 8),

                    // SLIDER
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        activeTrackColor: Paleta.texto,
                        inactiveTrackColor: const Color(0xFFEEEEEE),
                        thumbColor: Paleta.verde,
                        overlayColor: Paleta.verde.withOpacity(0.2),
                        trackHeight: 4,
                      ),
                      child: RangeSlider(
                        values: tempRango,
                        min: 0,
                        max: 7000,
                        onChanged: (val) =>
                            setModalState(() => tempRango = val),
                      ),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'S/ ${tempRango.start.toStringAsFixed(0)}',
                          style: const TextStyle(
                            color: Paleta.textoTenue,
                            fontSize: 12,
                          ),
                        ),
                        Text(
                          'S/ ${tempRango.end.toStringAsFixed(0)}',
                          style: const TextStyle(
                            color: Paleta.textoTenue,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    ...filtrosDisponibles.entries.map((entry) {
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            entry.key,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: entry.value.map((opcion) {
                              final activo = tempAtributo == opcion;
                              return _ChipSeleccion(
                                texto: opcion,
                                activo: activo,
                                onTap: () => setModalState(
                                  () =>
                                      tempAtributo = activo ? 'Todos' : opcion,
                                ),
                              );
                            }).toList(),
                          ),
                          const SizedBox(height: 16),
                        ],
                      );
                    }).toList(),

                    const SizedBox(height: 16),
                    // Botón aplicar
                    SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: ElevatedButton(
                        onPressed: () {
                          setState(() {
                            _rangoPrecio = tempRango;
                            _ordenSeleccionado = tempOrden;
                            _atributoSeleccionado = tempAtributo;
                          });
                          _aplicarFiltros();
                          Navigator.pop(context);
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Paleta.verde,
                          foregroundColor: Colors.black,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        child: Text(
                          'Ver ${tempLista.length} resultados',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    int filtrosActivos = 0;
    if (_atributoSeleccionado != 'Todos') filtrosActivos++;
    if (_rangoPrecio.start > 0 || _rangoPrecio.end < 7000) filtrosActivos++;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(
              _titulo,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Paleta.texto,
              ),
            ),
            Text(
              '${_productosFiltrados.length} productos',
              style: const TextStyle(fontSize: 12, color: Paleta.textoTenue),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search, color: Paleta.texto),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const BuscadorWidget()),
            ),
          ),
        ],
        iconTheme: const IconThemeData(color: Paleta.texto),
      ),
      body: Column(
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                GestureDetector(
                  onTap: _abrirModalFiltros,
                  child: _ChipFiltro(
                    texto: filtrosActivos > 0
                        ? 'Filtros ($filtrosActivos)'
                        : 'Filtros',
                    oscuro: true,
                  ),
                ),
                const SizedBox(width: 8),
                _ChipFiltro(texto: _ordenSeleccionado, icono: Icons.sort),

                if (_atributoSeleccionado != 'Todos') ...[
                  const SizedBox(width: 8),
                  GestureDetector(
                    onTap: () {
                      setState(() => _atributoSeleccionado = 'Todos');
                      _aplicarFiltros();
                    },
                    child: _ChipFiltro(
                      texto: _atributoSeleccionado,
                      verde: true,
                    ),
                  ),
                ],
              ],
            ),
          ),

          Expanded(
            child: _cargando
                ? const Center(
                    child: CircularProgressIndicator(color: Paleta.verde),
                  )
                : _productosFiltrados.isEmpty
                ? const Center(child: Text('No hay productos con esos filtros'))
                : GridView.builder(
                    padding: const EdgeInsets.all(16),
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 2,
                          mainAxisSpacing: 16,
                          crossAxisSpacing: 16,
                          childAspectRatio: 0.65,
                        ),
                    itemCount: _productosFiltrados.length,
                    itemBuilder: (_, i) {
                      return _TarjetaProductoCategoria(
                        producto: _productosFiltrados[i],
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _OpcionRadio extends StatelessWidget {
  const _OpcionRadio({
    required this.texto,
    required this.seleccionado,
    required this.onTap,
  });
  final String texto;
  final bool seleccionado;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8.0),
        child: Row(
          children: [
            Icon(
              seleccionado
                  ? Icons.radio_button_checked
                  : Icons.radio_button_unchecked,
              color: seleccionado ? Colors.black : Paleta.textoTenue,
              size: 20,
            ),
            const SizedBox(width: 12),
            Text(
              texto,
              style: TextStyle(
                fontSize: 14,
                color: seleccionado ? Paleta.texto : Paleta.textoSuave,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ChipSeleccion extends StatelessWidget {
  const _ChipSeleccion({
    required this.texto,
    required this.activo,
    required this.onTap,
  });
  final String texto;
  final bool activo;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: activo ? Paleta.verde : const Color(0xFFEEEEEE),
            width: activo ? 2 : 1,
          ),
        ),
        child: Text(
          texto,
          style: TextStyle(
            color: Paleta.texto,
            fontSize: 12,
            fontWeight: activo ? FontWeight.bold : FontWeight.normal,
          ),
        ),
      ),
    );
  }
}

class _ChipFiltro extends StatelessWidget {
  const _ChipFiltro({
    required this.texto,
    this.icono,
    this.oscuro = false,
    this.verde = false,
  });
  final String texto;
  final IconData? icono;
  final bool oscuro;
  final bool verde;

  @override
  Widget build(BuildContext context) {
    final bgColor = oscuro
        ? const Color(0xFF1C1C1E)
        : (verde ? const Color(0xFFE8F8F0) : Colors.white);
    final txtColor = oscuro
        ? Colors.white
        : (verde ? Paleta.verde : Paleta.texto);
    final borderColor = oscuro || verde
        ? Colors.transparent
        : const Color(0xFFEEEEEE);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: borderColor),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icono != null) ...[
            Icon(icono, size: 14, color: txtColor),
            const SizedBox(width: 4),
          ],
          Text(
            texto,
            style: TextStyle(
              color: txtColor,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _TarjetaProductoCategoria extends StatelessWidget {
  const _TarjetaProductoCategoria({required this.producto});
  final Producto producto;

  @override
  Widget build(BuildContext context) {
    String? urlImagen = producto.imagenes.isNotEmpty
        ? producto.imagenes.first
        : producto.imagenUrl;

    if (urlImagen != null &&
        urlImagen.isNotEmpty &&
        !urlImagen.startsWith('http')) {
      const String baseUrl = 'http://192.168.1.35:8080';
      urlImagen = urlImagen.startsWith('/')
          ? '$baseUrl$urlImagen'
          : '$baseUrl/$urlImagen';
    }

    return GestureDetector(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => PaginaProducto(productoId: producto.id),
        ),
      ),
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
                  height: 110,
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
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
                            color: Paleta.textoTenue,
                          ),
                        )
                      : const Icon(
                          Icons.devices_other,
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
                          fontSize: 9,
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
                      size: 14,
                      color: Paleta.textoTenue,
                    ),
                  ),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(10.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    (producto.marcaNombre ?? 'TECNOLOGÍA').toUpperCase(),
                    style: const TextStyle(
                      fontSize: 9,
                      color: Paleta.textoTenue,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    producto.nombre,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: Paleta.texto,
                      height: 1.2,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      const Icon(Icons.star, size: 10, color: Colors.amber),
                      const SizedBox(width: 2),
                      Text(
                        '${(producto.calificacionPromedio ?? 0).toStringAsFixed(1)} (${producto.cantidadValoraciones})',
                        style: const TextStyle(
                          fontSize: 9,
                          color: Paleta.textoTenue,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'S/ ${producto.precioActual.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: Paleta.texto,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.all(4),
                        decoration: BoxDecoration(
                          color: Paleta.verde,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Icon(
                          Icons.add,
                          size: 16,
                          color: Colors.white,
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
