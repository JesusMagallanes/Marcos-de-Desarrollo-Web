// lib/nucleo/modelos/mensaje_chat.dart

class MensajeChat {
  final String texto;
  final bool esUsuario;
  final ProductoRecomendado? producto;

  MensajeChat({required this.texto, required this.esUsuario, this.producto});
}

class ProductoRecomendado {
  final int id; // <-- AGREGADO
  final String categoria;
  final String nombre;
  final String descripcion;
  final double precio;
  final double? precioAnterior;
  final double rating;
  final int reviews;
  final String? imagenUrl;
  final int stock;

  bool get sinStock => stock <= 0;

  ProductoRecomendado({
    required this.id,
    required this.categoria,
    required this.nombre,
    required this.descripcion,
    required this.precio,
    this.precioAnterior,
    required this.rating,
    required this.reviews,
    this.imagenUrl,
    this.stock = 0,
  });
}
