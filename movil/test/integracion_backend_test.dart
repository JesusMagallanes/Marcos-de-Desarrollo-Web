@Tags(['integracion'])
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:smartzone_movil/nucleo/api/almacen_sesion.dart';
import 'package:smartzone_movil/nucleo/api/cliente_api.dart';
import 'package:smartzone_movil/nucleo/servicios/carrito_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/catalogo_servicio.dart';
import 'package:smartzone_movil/nucleo/servicios/sesion.dart';

/// Prueba de integración contra el backend REAL, con el mismo código que usa
/// la app: mismo ClienteApi, mismos servicios, mismos modelos.
///
/// Comprueba lo que se pidió: que el móvil vea los datos que se publican desde
/// la web y que se pueda entrar con una cuenta que ya existe.
///
/// Se salta sola si el backend no está levantado, para que no rompa a quien
/// ejecute las pruebas sin la pila arriba:
///
///   docker compose --profile neon up -d
///   flutter test test/integracion_backend_test.dart
///
/// El almacén de tokens se sustituye por uno en memoria porque el de verdad
/// usa Keystore/Keychain y eso necesita un dispositivo; lo demás es idéntico.
void main() {
  const urlBase = 'http://localhost:8080';
  late ClienteApi api;
  late _AlmacenMemoria almacen;
  bool hayBackend = false;

  // Se entra UNA sola vez para todas las pruebas y se reutiliza la sesión.
  //
  // No es un atajo: el backend limita los intentos de login a 10 cada 15
  // minutos por IP, así que una prueba que entrara de nuevo en cada caso
  // agotaba el cupo a la segunda o tercera pasada y a partir de ahí fallaba
  // con 429 fingiendo ser un error de la app. Es el propio control de fuerza
  // bruta funcionando, y la prueba tiene que convivir con él.
  setUpAll(() async {
    hayBackend = await _backendVivo(urlBase);
    if (!hayBackend) return;

    almacen = _AlmacenMemoria();
    api = ClienteApi(almacen: almacen);

    final error = await Sesion(api, almacen: almacen)
        .entrar('admin@smartzone.com', 'SmartZone2026!');
    if (error != null) {
      fail('No se pudo iniciar sesión para las pruebas: ${error.mensaje}');
    }
  });

  tearDownAll(() {
    if (hayBackend) api.cerrar();
  });

  test('el catálogo del móvil trae los mismos productos que la web', () async {
    if (!hayBackend) return;

    final catalogo = CatalogoServicio(api);
    final productos = await catalogo.productos();

    expect(productos, isNotEmpty, reason: 'La tienda debería tener productos');

    final p = productos.first;
    expect(p.id, greaterThan(0));
    expect(p.nombre, isNotEmpty);
    expect(p.categoriaNombre, isNotEmpty);
    // `precioActual` lo calcula el backend; si llegara a cero, el móvil estaría
    // enseñando precios que no son.
    expect(p.precioActual, greaterThan(0));
  });

  test('las categorías se leen sin necesidad de sesión', () async {
    if (!hayBackend) return;

    final categorias = await CatalogoServicio(api).categorias();
    expect(categorias, isNotEmpty);
    expect(categorias.first.slug, isNotEmpty);
  });

  test('el login de setUpAll dejó la sesión lista y con los tokens guardados', () async {
    if (!hayBackend) return;

    // El token guardado es lo que permite abrir la app ya dentro la próxima vez.
    expect(await almacen.tokenAcceso, isNotEmpty);
    expect(await almacen.tokenRefresco, isNotEmpty);
  });

  test('con la sesión guardada, restaurar() entra sin volver a pedir nada', () async {
    if (!hayBackend) return;

    // Como si se cerrara y volviera a abrir la app: el almacén conserva los
    // tokens del login anterior y NO se vuelve a tocar la contraseña. Esto es
    // exactamente "si ya tiene cuenta vigente, que entre solo".
    final alReabrir = Sesion(ClienteApi(almacen: almacen), almacen: almacen);
    await alReabrir.restaurar();

    expect(alReabrir.autenticado, isTrue);
    expect(alReabrir.usuario!.email, 'admin@smartzone.com');
    expect(alReabrir.usuario!.esAdmin, isTrue);
  });

  test('credenciales equivocadas no abren sesión', () async {
    if (!hayBackend) return;

    // Almacén aparte: este intento no debe pisar la sesión buena de las demás.
    final vacio = _AlmacenMemoria();
    final aparte = ClienteApi(almacen: vacio);
    final error = await Sesion(aparte, almacen: vacio)
        .entrar('admin@smartzone.com', 'ClaveIncorrecta1!');
    aparte.cerrar();

    // Puede ser 401 (contraseña mala) o 429 si el cupo de intentos ya se
    // agotó; en los dos casos lo que importa es que NO abre sesión.
    expect(error, isNotNull);
    expect(await vacio.tokenAcceso, isNull);
  });

  test('el carrito exige sesión y es el mismo que el de la web', () async {
    if (!hayBackend) return;

    // Sin sesión: el backend responde 401 y el servicio lo refleja. Se usa un
    // cliente con el almacén vacío para no tocar la sesión compartida.
    final sinTokens = ClienteApi(almacen: _AlmacenMemoria());
    final anonimo = CarritoServicio(sinTokens);
    await anonimo.cargar();
    expect(anonimo.error?.noAutenticado, isTrue);
    sinTokens.cerrar();

    // Con la sesión de setUpAll: se puede añadir y el carrito vuelve completo.
    final productos = await CatalogoServicio(api).productos();
    final conStock = productos.firstWhere((p) => !p.sinStock, orElse: () => productos.first);

    final carrito = CarritoServicio(api);
    await carrito.cargar();
    await carrito.vaciar();

    await carrito.agregar(conStock.id, 2);
    expect(carrito.error, isNull, reason: carrito.error?.mensaje ?? '');
    expect(carrito.carrito.items, isNotEmpty);
    expect(carrito.unidades, 2);
    expect(carrito.carrito.subtotal, greaterThan(0));

    // Se deja como estaba: estas pruebas corren contra datos reales.
    await carrito.vaciar();
    expect(carrito.carrito.estaVacio, isTrue);
  });

  test('las guías publicadas desde el panel llegan al móvil', () async {
    if (!hayBackend) return;

    // Es la comprobación de "lo que se lanza en la web se ve en la app": las
    // guías las crea el administrador y no hay nada escrito en el cliente.
    final datos = await api.get('/guias');
    expect(datos, isA<List>());
    expect((datos as List), isNotEmpty);
    expect((datos.first as Map)['titulo'], isNotEmpty);
  });
}

Future<bool> _backendVivo(String url) async {
  try {
    final cliente = HttpClient()..connectionTimeout = const Duration(seconds: 2);
    final peticion = await cliente.getUrl(Uri.parse('$url/actuator/health'));
    final respuesta = await peticion.close().timeout(const Duration(seconds: 3));
    cliente.close();
    return respuesta.statusCode == 200;
  } catch (_) {
    return false;
  }
}

/// Almacén en memoria: el real usa Keystore/Keychain y necesita dispositivo.
class _AlmacenMemoria implements AlmacenSesion {
  String? _acceso;
  String? _refresco;
  String? _rol;
  String? _email;

  @override
  Future<String?> get tokenAcceso async => _acceso;

  @override
  Future<String?> get tokenRefresco async => _refresco;

  @override
  Future<String?> get rol async => _rol;

  @override
  Future<String?> get email async => _email;

  @override
  Future<bool> get haySesion async => (_refresco ?? '').isNotEmpty;

  @override
  Future<void> guardar({
    required String tokenAcceso,
    required String tokenRefresco,
    required String rol,
    required String email,
  }) async {
    _acceso = tokenAcceso;
    _refresco = tokenRefresco;
    _rol = rol;
    _email = email;
  }

  @override
  Future<void> actualizarAcceso(String token) async => _acceso = token;

  @override
  Future<void> limpiar() async {
    _acceso = null;
    _refresco = null;
    _rol = null;
    _email = null;
  }
}
