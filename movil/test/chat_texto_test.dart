import 'package:flutter_test/flutter_test.dart';
import 'package:smartzone_movil/nucleo/servicios/chat_servicio.dart';

void main() {
  test('la respuesta HTML del chat se muestra como texto', () {
    expect(
      aTextoPlano('💻 <b>Esto es lo que tengo en Laptops:</b><br><br>👉 Toca'),
      '💻 Esto es lo que tengo en Laptops:\n\n👉 Toca',
    );
  });

  test('el texto escapado vuelve a su carácter, sin des-escapar dos veces', () {
    expect(
      aTextoPlano('a &lt;b&gt; &amp;lt; &quot;c&quot; &#39;d&#39;'),
      'a <b> &lt; "c" \'d\'',
    );
  });
}
