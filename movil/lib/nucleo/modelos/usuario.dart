/// Usuario autenticado. Nunca incluye la contraseña: el backend no la manda.
class Usuario {
  final int id;
  final String nombre;
  final String apellidos;
  final String email;
  final String telefono;
  final String direccion;
  final String rol;

  const Usuario({
    required this.id,
    required this.nombre,
    required this.apellidos,
    required this.email,
    required this.telefono,
    required this.direccion,
    required this.rol,
  });

  String get nombreCompleto => '$nombre $apellidos'.trim();

  /// Iniciales para el avatar, cuando no hay foto.
  String get iniciales {
    final n = nombre.isNotEmpty ? nombre[0] : '';
    final a = apellidos.isNotEmpty ? apellidos[0] : '';
    return '$n$a'.toUpperCase();
  }

  bool get esAdmin => rol == 'ADMINISTRADOR';
  bool get esStaff => esAdmin || rol == 'EMPLEADO';

  factory Usuario.desdeJson(Map<String, dynamic> j) => Usuario(
        id: (j['id'] as num).toInt(),
        nombre: (j['name'] ?? '') as String,
        apellidos: (j['lastname'] ?? '') as String,
        email: (j['emailAddress'] ?? '') as String,
        telefono: (j['phoneNumber'] ?? '') as String,
        direccion: (j['address'] ?? '') as String,
        rol: (j['rol'] ?? 'CLIENTE') as String,
      );
}
