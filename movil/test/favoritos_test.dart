import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:smartzone_movil/nucleo/api/almacen_sesion.dart';
import 'package:smartzone_movil/nucleo/api/cliente_api.dart';
import 'package:smartzone_movil/nucleo/modelos/usuario.dart';
import 'package:smartzone_movil/nucleo/servicios/carrito_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/catalogo_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/favoritos_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/sesion.dart';
import 'package:smartzone_movil/paginas/favoritos/pagina_favoritos.dart';

Future<void> _pump() => Future<void>.delayed(const Duration(milliseconds: 1));

class _AlmacenMemoria implements AlmacenSesion {
  @override
  Future<String?> get tokenAcceso async => null;

  @override
  Future<String?> get tokenRefresco async => null;

  @override
  Future<String?> get rol async => null;

  @override
  Future<String?> get email async => null;

  @override
  Future<bool> get haySesion async => false;

  @override
  Future<void> guardar({
    required String tokenAcceso,
    required String tokenRefresco,
    required String rol,
    required String email,
  }) async {}

  @override
  Future<void> actualizarAcceso(String token) async {}

  @override
  Future<void> limpiar() async {}
}

class _SesionFalsa extends Sesion {
  _SesionFalsa()
    : super(
        ClienteApi(almacen: _AlmacenMemoria(), http: http.Client()),
      );

  int? _id;

  @override
  Usuario? get usuario => _id == null
      ? null
      : Usuario(
          id: _id!,
          nombre: 'Usuario',
          apellidos: 'Prueba',
          email: 'usuario@prueba.com',
          telefono: '',
          direccion: '',
          rol: 'CLIENTE',
        );

  @override
  bool get autenticado => _id != null;

  Future<void> entrarComo(int id) async {
    _id = id;
    notifyListeners();
    await _pump();
  }

  void salirComo() {
    _id = null;
    notifyListeners();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('guarda favoritos por usuario y los aísla al cambiar de cuenta', () async {
    final preferencias = await SharedPreferences.getInstance();
    final sesion = _SesionFalsa();
    final servicio = FavoritosServicio(preferencias, sesion);
    await servicio.iniciar();

    expect(await servicio.alternar(99), isFalse);

    await sesion.entrarComo(1);
    expect(await servicio.alternar(10), isTrue);
    expect(await servicio.alternar(11), isTrue);
    expect(servicio.ids, {10, 11});
    expect(
      preferencias.getStringList('favoritos_usuario_1'),
      ['10', '11'],
    );

    await sesion.entrarComo(2);
    expect(servicio.cantidad, 0);
    expect(await servicio.alternar(20), isTrue);
    expect(preferencias.getStringList('favoritos_usuario_2'), ['20']);

    await sesion.entrarComo(1);
    expect(servicio.ids, {10, 11});
    expect(servicio.esFavorito(10), isTrue);

    sesion.salirComo();
    expect(servicio.cantidad, 0);
    expect(servicio.esFavorito(10), isFalse);
  });

  testWidgets('muestra el estado vacío cuando no hay favoritos', (tester) async {
    final preferencias = await SharedPreferences.getInstance();
    final sesion = _SesionFalsa();
    final servicio = FavoritosServicio(preferencias, sesion)..iniciar();
    final api = ClienteApi(
      almacen: _AlmacenMemoria(),
      http: MockClient(
        (_) async => http.Response('{"content":[]}', 200),
      ),
    );

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<ClienteApi>.value(value: api),
          ChangeNotifierProvider<Sesion>.value(value: sesion),
          Provider(create: (_) => CatalogoServicio(api)),
          ChangeNotifierProvider(create: (_) => CarritoServicio(api)),
          ChangeNotifierProvider<FavoritosServicio>.value(value: servicio),
        ],
        child: const MaterialApp(
          home: Scaffold(body: PaginaFavoritos()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mis favoritos'), findsOneWidget);
    expect(find.text('0 productos guardados'), findsOneWidget);
    expect(find.text('Aún no tienes productos favoritos'), findsOneWidget);
    expect(find.text('Explorar productos'), findsOneWidget);
  });
}
