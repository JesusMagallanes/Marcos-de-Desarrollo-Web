import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'nucleo/api/cliente_api.dart';
import 'nucleo/config/tema.dart';
import 'nucleo/servicios/carrito_servicio.dart';
import 'nucleo/servicios/catalogo_servicio.dart';
import 'nucleo/servicios/sesion.dart';
import 'paginas/arranque.dart';
import 'paginas/portero_inicial.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await initializeDateFormatting('es_PE');

  runApp(const AppSmartZone());
}

class AppSmartZone extends StatelessWidget {
  const AppSmartZone({super.key});

  @override
  Widget build(BuildContext context) {
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
        home: const PorteroInicial(), // <-- CAMBIADO
      ),
    );
  }
}
