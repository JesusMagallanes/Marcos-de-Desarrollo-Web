import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'nucleo/api/cliente_api.dart';
import 'nucleo/config/tema.dart';
import 'nucleo/servicios/carrito_servicio.dart';
import 'nucleo/servicios/catalogo_servicio.dart';
import 'nucleo/servicios/sesion.dart';
import 'paginas/arranque.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Formatos de fecha y moneda en español de Perú, como en la web.
  await initializeDateFormatting('es_PE');

  runApp(const AppSmartZone());
}

class AppSmartZone extends StatelessWidget {
  const AppSmartZone({super.key});

  @override
  Widget build(BuildContext context) {
    // Un único ClienteApi para toda la app: comparte el token y, sobre todo,
    // el candado del refresco. Con uno por servicio, tres peticiones que
    // caducan a la vez lanzarían tres refrescos y se invalidarían entre sí.
    final api = ClienteApi();

    return MultiProvider(
      providers: [
        Provider<ClienteApi>.value(value: api),
        ChangeNotifierProvider(create: (_) => Sesion(api)..restaurar()),
        Provider(create: (_) => CatalogoServicio(api)),
        ChangeNotifierProvider(create: (_) => CarritoServicio(api)),
      ],
      child: MaterialApp(
        title: 'SmartZone',
        debugShowCheckedModeBanner: false,
        theme: construirTema(),
        home: const Arranque(),
      ),
    );
  }
}
