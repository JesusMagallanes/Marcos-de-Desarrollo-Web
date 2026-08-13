/// Producto tal y como lo devuelve `catalogo`.
///
/// Los campos siguen los nombres del backend (`name`, `imageUrl`) aunque el
/// resto del código esté en español: renombrarlos aquí obligaría a mantener un
/// diccionario de equivalencias y a recordarlo en cada cambio del contrato.
class Producto {
  final int id;
  final String nombre;
  final String descripcion;
  final String? especificaciones;

  /// Precio de lista.
  final double precio;

  /// Lo que paga el cliente hoy: el de oferta si está vigente, si no el de
  /// lista. Lo calcula el backend para que web y móvil no repitan la lógica de
  /// fechas y puedan discrepar.
  final double precioActual;
  final bool enOferta;

  final double? calificacionPromedio;
  final int cantidadValoraciones;

  final String? imagenUrl;
  final List<String> imagenes;
  final int stock;

  final int categoriaId;
  final String categoriaNombre;
  final int? marcaId;
  final String? marcaNombre;

  const Producto({
    required this.id,
    required this.nombre,
    required this.descripcion,
    this.especificaciones,
    required this.precio,
    required this.precioActual,
    required this.enOferta,
    this.calificacionPromedio,
    required this.cantidadValoraciones,
    this.imagenUrl,
    required this.imagenes,
    required this.stock,
    required this.categoriaId,
    required this.categoriaNombre,
    this.marcaId,
    this.marcaNombre,
  });

  bool get sinStock => stock <= 0;

  /// Porcentaje de descuento redondeado, para la etiqueta de la tarjeta.
  int? get porcentajeDescuento {
    if (!enOferta || precio <= 0 || precioActual >= precio) return null;
    return (((precio - precioActual) / precio) * 100).round();
  }

  factory Producto.desdeJson(Map<String, dynamic> j) => Producto(
        id: j['id'] as int,
        nombre: (j['name'] ?? '') as String,
        descripcion: (j['description'] ?? '') as String,
        especificaciones: j['specifications'] as String?,
        precio: _aDouble(j['precio']),
        // `precioActual` llegó con las ofertas; si el backend fuera anterior,
        // se cae al precio de lista en vez de quedarse a cero.
        precioActual: j['precioActual'] != null ? _aDouble(j['precioActual']) : _aDouble(j['precio']),
        enOferta: (j['enOferta'] as bool?) ?? false,
        calificacionPromedio: j['calificacionPromedio'] == null
            ? null
            : _aDouble(j['calificacionPromedio']),
        cantidadValoraciones: (j['cantidadValoraciones'] as num?)?.toInt() ?? 0,
        imagenUrl: j['imageUrl'] as String?,
        imagenes: ((j['imagenes'] as List?) ?? const []).map((e) => e.toString()).toList(),
        stock: (j['stock'] as num?)?.toInt() ?? 0,
        categoriaId: (j['categoriaId'] as num).toInt(),
        categoriaNombre: (j['categoriaName'] ?? '') as String,
        marcaId: (j['marcaId'] as num?)?.toInt(),
        marcaNombre: j['marcaName'] as String?,
      );
}

/// El backend manda BigDecimal, que en JSON puede llegar como número o como
/// cadena según el serializador. Se aceptan ambos para no romper por eso.
double _aDouble(dynamic v) {
  if (v == null) return 0;
  if (v is num) return v.toDouble();
  return double.tryParse(v.toString()) ?? 0;
}
