import 'package:intl/intl.dart';

/// Formato de precio idéntico al de la web (`| currency: 'PEN' : 'S/ '`).
///
/// Se define una vez porque aparece en la tarjeta, en el detalle, en el
/// carrito y en el resumen del pedido: cada sitio con su propio `toString`
/// acaba enseñando "S/ 1399.9" en una pantalla y "S/ 1,399.90" en la de al
/// lado.
final _formatoSoles = NumberFormat.currency(
  locale: 'es_PE',
  symbol: 'S/ ',
  decimalDigits: 2,
);

String soles(double valor) => _formatoSoles.format(valor);
