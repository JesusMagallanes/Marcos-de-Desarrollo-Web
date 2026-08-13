import 'package:intl/intl.dart';

/// Formato de precio en soles, igual que el de la web.
///
/// La web usa `| currency: 'PEN' : 'S/ '` con la configuración es-PE de
/// Angular, que pinta `S/ 5,499.90`: símbolo delante, coma para los miles y
/// punto para los decimales.
///
/// OJO con el idioma que se pasa aquí: `es_PE` en ICU (lo que usa Dart) tiene
/// otra convención y devuelve `5.499,90 S/`, con el símbolo detrás y los
/// separadores al revés. Se veía así en la app mientras la web mostraba otra
/// cosa para el mismo producto, que es justo lo que no puede pasar en una
/// tienda: el precio tiene que leerse igual en los dos sitios.
///
/// Por eso se usa el patrón numérico de `en_US`, que coincide con el que
/// aplica Angular, y se le pone el símbolo de soles.
final _formatoSoles = NumberFormat.currency(
  locale: 'en_US',
  symbol: 'S/ ',
  decimalDigits: 2,
);

String soles(double valor) => _formatoSoles.format(valor);
