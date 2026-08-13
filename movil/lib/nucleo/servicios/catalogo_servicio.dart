import '../api/cliente_api.dart';
import '../modelos/categoria.dart';
import '../modelos/producto.dart';

/// Lectura del catálogo. Todo es público: no hace falta sesión para mirar la
/// tienda, igual que en la web.
class CatalogoServicio {
  CatalogoServicio(this._api);
  final ClienteApi _api;

  /// Caché en memoria del listado completo. La portada y el buscador lo piden
  /// varias veces por sesión y el catálogo cambia poco.
  List<Producto>? _cacheProductos;
  List<Categoria>? _cacheCategorias;

  Future<List<Producto>> productos({String? buscar, bool refrescar = false}) async {
    if (buscar != null && buscar.trim().isNotEmpty) {
      final datos = await _api.get('/productos', consulta: {'search': buscar.trim()});
      return _aProductos(datos);
    }

    if (_cacheProductos != null && !refrescar) return _cacheProductos!;
    final datos = await _api.get('/productos');
    return _cacheProductos = _aProductos(datos);
  }

  Future<Producto> producto(int id) async {
    final datos = await _api.get('/productos/$id');
    return Producto.desdeJson(datos as Map<String, dynamic>);
  }

  Future<List<Categoria>> categorias({bool refrescar = false}) async {
    if (_cacheCategorias != null && !refrescar) return _cacheCategorias!;
    final datos = await _api.get('/categorias');
    return _cacheCategorias = ((datos as List?) ?? const [])
        .map((e) => Categoria.desdeJson(e as Map<String, dynamic>))
        .toList();
  }

  /// El backend pagina esta ruta; se pide la primera página con tope alto
  /// porque la app pinta un listado corto por categoría.
  Future<List<Producto>> porCategoria(String slug, {int pagina = 0, int tamano = 20}) async {
    final datos = await _api.get('/productos/categoria/$slug',
        consulta: {'page': '$pagina', 'size': '$tamano'});
    final mapa = datos as Map<String, dynamic>;
    return _aProductos(mapa['content']);
  }

  /// Lo que ya está en memoria, sin esperar.
  ///
  /// Lo usa la tienda para pintarse de golpe cuando el arranque ya precargó el
  /// catálogo: si tuviera que pasar por el Future, enseñaría su spinner un
  /// instante justo después de quitarse la pantalla de carga, que es el
  /// parpadeo que se quiso evitar.
  List<Producto>? get productosEnCache => _cacheProductos;
  List<Categoria>? get categoriasEnCache => _cacheCategorias;

  void invalidar() {
    _cacheProductos = null;
    _cacheCategorias = null;
  }

  List<Producto> _aProductos(dynamic lista) => ((lista as List?) ?? const [])
      .map((e) => Producto.desdeJson(e as Map<String, dynamic>))
      .toList();
}
