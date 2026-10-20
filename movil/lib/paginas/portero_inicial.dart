import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'arranque.dart';
import 'bienvenida/bienvenida_screen.dart';
import 'login/pagina_login.dart';

class PorteroInicial extends StatefulWidget {
  const PorteroInicial({super.key});

  @override
  State<PorteroInicial> createState() => _PorteroInicialState();
}

class _PorteroInicialState extends State<PorteroInicial> {
  final _storage = const FlutterSecureStorage();
  bool? _vioBienvenida;

  @override
  void initState() {
    super.initState();
    _revisarSiEsPrimeraVez();
  }

  Future<void> _revisarSiEsPrimeraVez() async {
    try {
      final vio = await _storage.read(key: 'vio_bienvenida');
      if (mounted) {
        setState(() => _vioBienvenida = vio == 'true');
      }
    } catch (e) {
      if (mounted) setState(() => _vioBienvenida = false);
    }
  }

  Future<void> _marcarComoVista() async {
    try {
      await _storage.write(key: 'vio_bienvenida', value: 'true');
    } catch (e) {
      // Ignorar error de guardado
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_vioBienvenida == null) {
      return const Scaffold(backgroundColor: Color(0xFFF8F9FA));
    }

    if (_vioBienvenida!) {
      return const Arranque();
    }

    // Si es la primera vez, muestra la pantalla de bienvenida
    return BienvenidaScreen(
      // "Comenzar" te lleva al Login
      alTerminar: () async {
        await _marcarComoVista();
        if (context.mounted) {
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(builder: (context) => const PaginaLogin()),
          );
        }
      },

      alOmitir: () async {
        await _marcarComoVista();
        if (context.mounted) {
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(builder: (context) => const Arranque()),
          );
        }
      },
    );
  }
}
