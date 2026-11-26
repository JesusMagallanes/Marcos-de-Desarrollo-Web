/// Dirección única guardada en el perfil, tal como la devuelve `usuarios`.
///
/// El backend mantiene una sola dirección por usuario (`direccion` dentro de
/// `/auth/yo`), por lo que no hay lista de direcciones ni id de dirección.
class Direccion {
  final String calle;
  final String numero;
  final String referencia;
  final String codigoPostal;
  final String distrito;
  final String provincia;
  final String departamento;
  final String pais;
  final double? latitud;
  final double? longitud;

  const Direccion({
    required this.calle,
    required this.numero,
    required this.referencia,
    required this.codigoPostal,
    required this.distrito,
    required this.provincia,
    required this.departamento,
    required this.pais,
    this.latitud,
    this.longitud,
  });

  bool get estaVacia => calle.trim().isEmpty || numero.trim().isEmpty;

  String get enUnaLinea {
    final localidad = provincia.equalsIgnoreCase(departamento)
        ? distrito
        : '$distrito, $provincia, $departamento';
    return '$calle $numero, $localidad';
  }

  Map<String, dynamic> toJson() => {
        'calle': calle.trim(),
        'numero': numero.trim(),
        'referencia': referencia.trim(),
        'codigoPostal': codigoPostal.trim(),
        'distrito': distrito.trim(),
        'provincia': provincia.trim(),
        'departamento': departamento.trim(),
        'pais': pais.trim().isEmpty ? 'PE' : pais.trim().toUpperCase(),
        if (latitud != null && longitud != null) ...{
          'latitud': latitud,
          'longitud': longitud,
        },
      };

  factory Direccion.desdeJson(Map<String, dynamic> j) => Direccion(
        calle: (j['calle'] ?? '') as String,
        numero: (j['numero'] ?? '') as String,
        referencia: (j['referencia'] ?? '') as String,
        codigoPostal: (j['codigoPostal'] ?? '') as String,
        distrito: (j['distrito'] ?? '') as String,
        provincia: (j['provincia'] ?? '') as String,
        departamento: (j['departamento'] ?? '') as String,
        pais: (j['pais'] ?? 'PE') as String,
        latitud: _aDouble(j['latitud']),
        longitud: _aDouble(j['longitud']),
      );
}

extension _IgualdadInsensible on String {
  bool equalsIgnoreCase(String otro) => toLowerCase() == otro.toLowerCase();
}

double? _aDouble(dynamic v) {
  if (v == null) return null;
  if (v is num) return v.toDouble();
  return double.tryParse(v.toString());
}
