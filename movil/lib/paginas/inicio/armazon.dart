import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../carrito/pagina_carrito.dart';
import '../perfil/pagina_perfil.dart';
import 'pagina_tienda.dart';

/// Armazón con la barra inferior. Es la navegación principal de la app.
///
/// En la web esto es la cabecera con buscador, carrito y "mi cuenta"; en móvil
/// se reparte en pestañas, que es lo que se espera con el pulgar.
class Armazon extends StatefulWidget {
  const Armazon({super.key});

  @override
  State<Armazon> createState() => _ArmazonEstado();
}

class _ArmazonEstado extends State<Armazon> {
  int _indice = 0;

  /// Se conservan vivas para no recargar la tienda al ir y volver del carrito.
  final _paginas = const [PaginaTienda(), PaginaCarrito(), PaginaPerfil()];

  @override
  void initState() {
    super.initState();
    // Si ya había sesión, se trae el carrito para que el contador de la barra
    // sea correcto desde el primer momento.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<Sesion>().autenticado) {
        context.read<CarritoServicio>().cargar();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final unidades = context.watch<CarritoServicio>().unidades;
    final autenticado = context.watch<Sesion>().autenticado;

    return Scaffold(
      body: IndexedStack(index: _indice, children: _paginas),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _indice,
        onDestinationSelected: (i) => setState(() => _indice = i),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            selectedIcon: Icon(Icons.storefront, color: Paleta.verde),
            label: 'Tienda',
          ),
          NavigationDestination(
            icon: Badge(
              // El contador solo aparece si hay algo; un "0" permanente es
              // ruido y además confunde con un carrito con artículos.
              isLabelVisible: unidades > 0,
              label: Text('$unidades'),
              backgroundColor: Paleta.verde,
              child: const Icon(Icons.shopping_cart_outlined),
            ),
            selectedIcon: Badge(
              isLabelVisible: unidades > 0,
              label: Text('$unidades'),
              backgroundColor: Paleta.verde,
              child: const Icon(Icons.shopping_cart, color: Paleta.verde),
            ),
            label: 'Carrito',
          ),
          NavigationDestination(
            icon: const Icon(Icons.person_outline),
            selectedIcon: const Icon(Icons.person, color: Paleta.verde),
            label: autenticado ? 'Mi cuenta' : 'Entrar',
          ),
        ],
      ),
    );
  }
}
