import '../api/cliente_api.dart';
import '../modelos/direccion.dart';
import '../modelos/metodo_pago.dart';
import '../modelos/pedido.dart';

/// Lecturas del microservicio de compras y escritura de la dirección del perfil.
///
/// No guarda copias de los pedidos: cada pantalla pide los suyos al backend para
/// que los estados nunca se queden congelados en el móvil.
class ComprasServicio {
  ComprasServicio(this._api);
  final ClienteApi _api;

  Future<List<Pedido>> misPedidos() async {
    final datos = await _api.get('/pedidos/mios');
    return _lista(datos).map((e) => Pedido.desdeJson(e)).toList();
  }

  Future<Pedido> pedido(int id) async {
    final datos = await _api.get('/pedidos/$id');
    return Pedido.desdeJson(datos as Map<String, dynamic>);
  }

  Future<List<Envio>> misEnvios() async {
    final datos = await _api.get('/envios/mios');
    return _lista(datos).map((e) => Envio.desdeJson(e)).toList();
  }

  Future<List<MetodoPago>> metodosPago() async {
    final datos = await _api.get('/metodos-pago');
    return _lista(datos).map((e) => MetodoPago.desdeJson(e)).toList();
  }

  /// El backend valida los mismos límites que [Direccion] y actualiza también
  /// la línea de texto del perfil. El id del usuario NO se toma de aquí: sale
  /// del token.
  Future<void> guardarDireccion(int usuarioId, Direccion direccion) =>
      _api.put('/usuarios/$usuarioId/direccion', cuerpo: direccion.toJson());

  List<Map<String, dynamic>> _lista(dynamic datos) => ((datos as List?) ?? const [])
      .map((e) => e as Map<String, dynamic>)
      .toList();
}
