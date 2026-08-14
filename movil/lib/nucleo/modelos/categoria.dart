/// Categoría del catálogo. `slug` es lo que viaja en las rutas de la API.
class Categoria {
  final int id;
  final String nombre;
  final String slug;
  final String descripcion;
  final String? icono;

  const Categoria({
    required this.id,
    required this.nombre,
    required this.slug,
    required this.descripcion,
    this.icono,
  });

  factory Categoria.desdeJson(Map<String, dynamic> j) => Categoria(
        id: j['id'] as int,
        nombre: (j['name'] ?? '') as String,
        slug: (j['slug'] ?? '') as String,
        descripcion: (j['description'] ?? '') as String,
        icono: j['icono'] as String?,
      );
}
