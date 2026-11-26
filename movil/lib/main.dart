import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'nucleo/api/cliente_api.dart';
import 'nucleo/config/tema.dart';
import 'nucleo/servicios/carrito_servicio.dart';
import 'nucleo/servicios/catalogo_servicio.dart';
import 'nucleo/servicios/compras_servicio.dart';
import 'nucleo/servicios/favoritos_servicio.dart';
import 'nucleo/servicios/sesion.dart';
import 'paginas/portero_inicial.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await initializeDateFormatting('es_PE');
  final preferencias = await SharedPreferences.getInstance();

  runApp(AppSmartZone(preferencias: preferencias));
}

class AppSmartZone extends StatelessWidget {
  const AppSmartZone({super.key, required this.preferencias});

  final SharedPreferences preferencias;

  @override
  Widget build(BuildContext context) {
    final api = ClienteApi();

    return MultiProvider(
      providers: [
        Provider<ClienteApi>.value(value: api),
        ChangeNotifierProvider(create: (_) => Sesion(api)..restaurar()),
        Provider(create: (_) => CatalogoServicio(api)),
        Provider(create: (_) => ComprasServicio(api)),
        ChangeNotifierProvider(create: (_) => CarritoServicio(api)),
        ChangeNotifierProvider(
          create: (context) =>
              FavoritosServicio(preferencias, context.read<Sesion>())..iniciar(),
        ),
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
