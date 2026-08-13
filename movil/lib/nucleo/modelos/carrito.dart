/// Una línea del carrito. `stockDisponible` viene del backend para poder
/// avisar antes de que el checkout falle por falta de existencias.
class ItemCarrito {
  final int itemId;
  final int productoId;
  final String nombre;
  final double precio;
  final int cantidad;
  final String? imagen;
  final int stockDisponible;

  const ItemCarrito({
    required this.itemId,
    required this.productoId,
    required this.nombre,
    required this.precio,
    required this.cantidad,
    this.imagen,
    required this.stockDisponible,
  });

  double get total => precio * cantidad;
  bool get excedeStock => cantidad > stockDisponible;

  factory ItemCarrito.desdeJson(Map<String, dynamic> j) => ItemCarrito(
        itemId: (j['itemId'] as num).toInt(),
        productoId: (j['productId'] as num).toInt(),
        nombre: (j['nombre'] ?? '') as String,
        precio: _aDouble(j['precio']),
        cantidad: (j['cantidad'] as num?)?.toInt() ?? 0,
        imagen: j['image'] as String?,
        stockDisponible: (j['stockDisponible'] as num?)?.toInt() ?? 0,
      );
}

/// El backend devuelve el carrito completo tras CADA operación, así que no
/// hace falta pedirlo otra vez después de añadir o quitar.
class Carrito {
  final List<ItemCarrito> items;
  final double subtotal;

  const Carrito({required this.items, required this.subtotal});

  const Carrito.vacio() : items = const [], subtotal = 0;

  bool get estaVacio => items.isEmpty;
  int get unidades => items.fold(0, (n, i) => n + i.cantidad);

  factory Carrito.desdeJson(Map<String, dynamic> j) => Carrito(
        items: ((j['items'] as List?) ?? const [])
            .map((e) => ItemCarrito.desdeJson(e as Map<String, dynamic>))
            .toList(),
        subtotal: _aDouble(j['subtotal']),
      );
}

double _aDouble(dynamic v) {
  if (v == null) return 0;
  if (v is num) return v.toDouble();
  return double.tryParse(v.toString()) ?? 0;
}
