import 'package:flutter_test/flutter_test.dart';
import 'package:smartzone_movil/nucleo/api/error_api.dart';
import 'package:smartzone_movil/nucleo/modelos/carrito.dart';
import 'package:smartzone_movil/nucleo/modelos/producto.dart';

/// Pruebas de la capa que traduce lo que responde el backend.
///
/// Es donde de verdad se rompe una app móvil: el contrato lo decide el
/// servidor y cualquier cambio ahí llega en forma de JSON con un campo que
/// falta o que viene con otro tipo.
void main() {
  group('Producto.desdeJson', () {
    test('lee el producto completo que devuelve el catálogo', () {
      final p = Producto.desdeJson({
        'id': 1,
        'name': 'Asus ROG Strix G16',
        'description': 'Laptop gamer',
        'specifications': 'RAM: 16 GB',
        'precio': 5499.90,
        'precioActual': 4999.90,
        'enOferta': true,
        'calificacionPromedio': 4.5,
        'cantidadValoraciones': 12,
        'imageUrl': '/Img/laptop.webp',
        'imagenes': ['/Img/laptop.webp'],
        'stock': 5,
        'categoriaId': 1,
        'categoriaName': 'Laptops',
        'marcaId': 1,
        'marcaName': 'Asus',
      });

      expect(p.nombre, 'Asus ROG Strix G16');
      expect(p.precioActual, 4999.90);
      expect(p.enOferta, isTrue);
      expect(p.sinStock, isFalse);
      // (5499.90 - 4999.90) / 5499.90 = 9,09 % -> 9
      expect(p.porcentajeDescuento, 9);
    });

    test('sobrevive a los campos opcionales ausentes', () {
      final p = Producto.desdeJson({
        'id': 2,
        'name': 'Monitor',
        'description': '27 pulgadas',
        'precio': 1399.90,
        'stock': 0,
        'categoriaId': 2,
        'categoriaName': 'Monitores',
      });

      expect(p.especificaciones, isNull);
      expect(p.marcaNombre, isNull);
      expect(p.imagenes, isEmpty);
      expect(p.cantidadValoraciones, 0);
      expect(p.sinStock, isTrue);
      // Sin `precioActual` se cae al precio de lista, nunca a cero.
      expect(p.precioActual, 1399.90);
      expect(p.porcentajeDescuento, isNull);
    });

    test('acepta los importes como número o como cadena', () {
      // Jackson serializa BigDecimal de una u otra forma según la
      // configuración; la app no debería enterarse.
      final comoTexto = Producto.desdeJson({
        'id': 3, 'name': 'X', 'description': 'Y', 'precio': '99.90',
        'stock': 1, 'categoriaId': 1, 'categoriaName': 'C',
      });
      expect(comoTexto.precio, 99.90);
    });
  });

  group('Carrito', () {
    test('suma unidades y detecta líneas que superan el stock', () {
      final c = Carrito.desdeJson({
        'subtotal': 300.0,
        'items': [
          {
            'itemId': 1, 'productId': 10, 'nombre': 'A', 'precio': 100.0,
            'cantidad': 2, 'image': null, 'stockDisponible': 5,
          },
          {
            'itemId': 2, 'productId': 11, 'nombre': 'B', 'precio': 100.0,
            'cantidad': 1, 'image': null, 'stockDisponible': 0,
          },
        ],
      });

      expect(c.unidades, 3);
      expect(c.estaVacio, isFalse);
      expect(c.items.first.total, 200.0);
      expect(c.items.first.excedeStock, isFalse);
      // Se agotó desde que se añadió: hay que poder avisar antes de pagar.
      expect(c.items.last.excedeStock, isTrue);
    });

    test('un carrito sin items está vacío', () {
      final c = Carrito.desdeJson({'items': [], 'subtotal': 0});
      expect(c.estaVacio, isTrue);
      expect(c.unidades, 0);
    });
  });

  group('ErrorApi', () {
    test('lee el formato RFC 7807 del backend', () {
      final e = ErrorApi.desdeJson(400, {
        'title': 'Datos inválidos',
        'detail': 'Revisa los campos del formulario',
        'errores': {'cantidad': 'La cantidad máxima por producto es 1000'},
      });

      expect(e.estado, 400);
      expect(e.titulo, 'Datos inválidos');
      expect(e.errores['cantidad'], contains('1000'));
      // El mensaje completo suma el detalle del campo, que es lo accionable.
      expect(e.mensajeCompleto, contains('1000'));
    });

    test('distingue sesión caducada de falta de permisos', () {
      expect(ErrorApi.desdeJson(401, const {}).noAutenticado, isTrue);
      expect(ErrorApi.desdeJson(403, const {}).sinPermiso, isTrue);
      expect(ErrorApi.desdeJson(429, const {}).demasiadasPeticiones, isTrue);
    });
  });
}
