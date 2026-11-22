import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:smartzone_movil/nucleo/modelos/carrito.dart';
import 'package:smartzone_movil/nucleo/modelos/producto.dart';
import 'package:smartzone_movil/widgets/confirmacion_agregar.dart';

Producto _producto({
  required int id,
  required String nombre,
  required double precio,
}) {
  return Producto(
    id: id,
    nombre: nombre,
    descripcion: 'Descripción de $nombre',
    precio: precio,
    precioActual: precio,
    enOferta: false,
    calificacionPromedio: 4.5,
    cantidadValoraciones: 12,
    imagenes: const [],
    stock: 5,
    categoriaId: 1,
    categoriaNombre: 'Tecnología',
    especificaciones: 'Memoria 16 GB',
  );
}

Widget _app({required VoidCallback alIrAlCarrito}) {
  final producto = _producto(id: 1, nombre: 'Laptop Smart', precio: 1200);
  final item = ItemCarrito(
    itemId: 10,
    productoId: producto.id,
    nombre: producto.nombre,
    precio: producto.precioActual,
    cantidad: 2,
    stockDisponible: producto.stock,
  );
  final carrito = Carrito(items: [item], subtotal: item.total);
  final recomendado = _producto(id: 2, nombre: 'Mouse Inalámbrico', precio: 50);

  return MaterialApp(
    home: Scaffold(
      body: Builder(
        builder: (context) => ElevatedButton(
          onPressed: () => ConfirmacionAgregar.mostrar(
            context: context,
            producto: producto,
            item: item,
            carrito: carrito,
            recomendados: [recomendado],
            alIrAlCarrito: alIrAlCarrito,
          ),
          child: const Text('Abrir'),
        ),
      ),
    ),
  );
}

void main() {
  testWidgets('muestra la confirmación y cierra con Seguir comprando',
      (tester) async {
    await tester.pumpWidget(_app(alIrAlCarrito: () {}));
    await tester.tap(find.text('Abrir'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Producto agregado al carrito'), findsOneWidget);
    expect(find.text('¡Listo! Se agregó a tu carrito'), findsOneWidget);
    expect(find.text('2 productos · Total S/ 2,400.00'), findsOneWidget);
    expect(find.text('Laptop Smart'), findsOneWidget);
    expect(find.text('Combina con'), findsOneWidget);
    expect(find.text('Mouse Inalámbrico'), findsOneWidget);
    expect(find.text('Cantidad: 2'), findsOneWidget);

    await tester.ensureVisible(find.text('Seguir comprando'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Seguir comprando'));
    await tester.pumpAndSettle();

    expect(find.text('¡Listo! Se agregó a tu carrito'), findsNothing);
    expect(find.text('Producto agregado al carrito'), findsOneWidget);

    await tester.pump(const Duration(seconds: 4));
    expect(find.text('Producto agregado al carrito'), findsNothing);
  });

  testWidgets('Ir al carrito cierra el panel y ejecuta la navegación',
      (tester) async {
    var navego = false;
    await tester.pumpWidget(_app(alIrAlCarrito: () => navego = true));
    await tester.tap(find.text('Abrir'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    await tester.ensureVisible(find.text('Ir al carrito'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ir al carrito'));
    await tester.pumpAndSettle();

    expect(navego, isTrue);
    expect(find.text('¡Listo! Se agregó a tu carrito'), findsNothing);

    await tester.pump(const Duration(seconds: 4));
  });
}
