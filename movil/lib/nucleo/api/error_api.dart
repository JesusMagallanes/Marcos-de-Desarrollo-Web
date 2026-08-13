/// Un error devuelto por la API, ya traducido a algo que se puede enseñar.
///
/// El backend responde siempre en formato RFC 7807 (ProblemDetail), el mismo
/// que consume la web:
///
///   {"type":"about:blank","title":"Datos inválidos","status":400,
///    "detail":"Revisa los campos del formulario",
///    "errores":{"precio":"El precio admite como mucho 2 decimales"}}
///
/// Se conserva `errores` porque es lo que permite marcar el campo concreto en
/// un formulario en vez de soltar un mensaje genérico.
class ErrorApi implements Exception {
  final int estado;
  final String titulo;
  final String mensaje;
  final Map<String, String> errores;

  const ErrorApi({
    required this.estado,
    required this.titulo,
    required this.mensaje,
    this.errores = const {},
  });

  /// Sin respuesta del servidor: no hay red, está caído o no se llega a él.
  factory ErrorApi.sinConexion(String detalle) => ErrorApi(
        estado: 0,
        titulo: 'Sin conexión',
        mensaje: 'No se pudo contactar con la tienda. $detalle',
      );

  factory ErrorApi.desdeJson(int estado, Map<String, dynamic> cuerpo) {
    final crudos = cuerpo['errores'];
    return ErrorApi(
      estado: estado,
      titulo: (cuerpo['title'] as String?) ?? 'Error',
      mensaje: (cuerpo['detail'] as String?) ?? 'Ocurrió un problema inesperado.',
      errores: crudos is Map
          ? crudos.map((k, v) => MapEntry(k.toString(), v.toString()))
          : const {},
    );
  }

  bool get noAutenticado => estado == 401;
  bool get sinPermiso => estado == 403;
  bool get noEncontrado => estado == 404;
  bool get demasiadasPeticiones => estado == 429;

  /// Mensaje listo para enseñar, con los errores de campo si los hay.
  String get mensajeCompleto {
    if (errores.isEmpty) return mensaje;
    return '$mensaje\n${errores.values.join('\n')}';
  }

  @override
  String toString() => 'ErrorApi($estado, $titulo): $mensaje';
}
