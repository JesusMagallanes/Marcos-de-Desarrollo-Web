/// Línea de un pedido. El backend guarda una instantánea del producto, así que
/// aquí se pinta lo que se compró, no lo que cueste hoy.
class DetallePedido {
  final int productoId;
  final String nombre;
  final String? imagen;
  final int cantidad;
  final double precioUnitario;
  final double total;

  const DetallePedido({
    required this.productoId,
    required this.nombre,
    required this.imagen,
    required this.cantidad,
    required this.precioUnitario,
    required this.total,
  });

  factory DetallePedido.desdeJson(Map<String, dynamic> j) => DetallePedido(
        productoId: (j['productoId'] as num).toInt(),
        nombre: (j['productoNombre'] ?? '') as String,
        imagen: j['imagen'] as String?,
        cantidad: (j['cantidad'] as num?)?.toInt() ?? 0,
        precioUnitario: _aDouble(j['precioUnitario']),
        total: _aDouble(j['total']),
      );
}

/// Pedido real del usuario autenticado.
class Pedido {
  final int id;
  final String numero;
  final DateTime? fecha;
  final String estado;
  final double subtotal;
  final double costoEnvio;
  final double total;
  final String metodoPago;
  final List<DetallePedido> detalles;

  const Pedido({
    required this.id,
    required this.numero,
    required this.fecha,
    required this.estado,
    required this.subtotal,
    required this.costoEnvio,
    required this.total,
    required this.metodoPago,
    required this.detalles,
  });

  bool get enCurso => estado == 'CONFIRMADO' || estado == 'PAGADO' || estado == 'EN_TRANSITO';
  bool get entregado => estado == 'ENTREGADO';
  bool get cancelado => estado == 'CANCELADO';

  String get etiquetaEstado {
    switch (estado) {
      case 'CONFIRMADO':
        return 'Confirmado';
      case 'PAGADO':
        return 'Pagado';
      case 'EN_TRANSITO':
        return 'En reparto';
      case 'ENTREGADO':
        return 'Entregado';
      case 'CANCELADO':
        return 'Cancelado';
      default:
        return 'Pendiente';
    }
  }

  factory Pedido.desdeJson(Map<String, dynamic> j) => Pedido(
        id: (j['id'] as num).toInt(),
        numero: (j['numero'] ?? '') as String,
        fecha: j['fecha'] == null ? null : DateTime.tryParse(j['fecha'].toString()),
        estado: (j['estado'] ?? 'PENDIENTE') as String,
        subtotal: _aDouble(j['subtotal']),
        costoEnvio: _aDouble(j['costoEnvio']),
        total: _aDouble(j['total']),
        metodoPago: (j['metodoPago'] ?? '') as String,
        detalles: ((j['detalles'] as List?) ?? const [])
            .map((e) => DetallePedido.desdeJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// Envío asociado a un pedido. La logística es un estado distinto del pago.
class Envio {
  final int id;
  final int pedidoId;
  final String direccion;
  final String? referencia;
  final String? telefonoContacto;
  final String? receptorNombre;
  final String? calle;
  final String? numero;
  final String? codigoPostal;
  final String? distrito;
  final String? provincia;
  final String? departamento;
  final String? pais;
  final String estadoEnvio;
  final DateTime? fechaProgramada;
  final DateTime? fechaEntregada;
  final double? distanciaKm;
  final int? minutosEstimados;
  final bool distanciaEsEstimada;

  const Envio({
    required this.id,
    required this.pedidoId,
    required this.direccion,
    required this.referencia,
    required this.telefonoContacto,
    required this.receptorNombre,
    required this.calle,
    required this.numero,
    required this.codigoPostal,
    required this.distrito,
    required this.provincia,
    required this.departamento,
    required this.pais,
    required this.estadoEnvio,
    required this.fechaProgramada,
    required this.fechaEntregada,
    required this.distanciaKm,
    required this.minutosEstimados,
    required this.distanciaEsEstimada,
  });

  String get etiquetaEstado {
    switch (estadoEnvio) {
      case 'EN_TRANSITO':
        return 'En reparto';
      case 'ENTREGADO':
        return 'Entregado';
      default:
        return 'Preparando';
    }
  }

  factory Envio.desdeJson(Map<String, dynamic> j) => Envio(
        id: (j['id'] as num).toInt(),
        pedidoId: (j['pedidoId'] as num).toInt(),
        direccion: (j['direccion'] ?? '') as String,
        referencia: j['referencia'] as String?,
        telefonoContacto: j['telefonoContacto'] as String?,
        receptorNombre: j['receptorNombre'] as String?,
        calle: j['calle'] as String?,
        numero: j['numero'] as String?,
        codigoPostal: j['codigoPostal'] as String?,
        distrito: j['distrito'] as String?,
        provincia: j['provincia'] as String?,
        departamento: j['departamento'] as String?,
        pais: j['pais'] as String?,
        estadoEnvio: (j['estadoEnvio'] ?? 'PENDIENTE') as String,
        fechaProgramada:
            j['fechaEnvioProgramado'] == null ? null : DateTime.tryParse(j['fechaEnvioProgramado'].toString()),
        fechaEntregada:
            j['fechaEnvioEntregado'] == null ? null : DateTime.tryParse(j['fechaEnvioEntregado'].toString()),
        distanciaKm: _aDouble(j['distanciaKm']),
        minutosEstimados: (j['minutosEstimados'] as num?)?.toInt(),
        distanciaEsEstimada: (j['distanciaEsEstimada'] as bool?) ?? false,
      );
}

double _aDouble(dynamic v) {
  if (v == null) return 0;
  if (v is num) return v.toDouble();
  return double.tryParse(v.toString()) ?? 0;
}
