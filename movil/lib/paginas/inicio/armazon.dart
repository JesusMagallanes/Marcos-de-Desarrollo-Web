import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../carrito/pagina_carrito.dart';
import '../categorias/pagina_categoria_productos.dart'; // <-- IMPORTANTE
import '../categorias/pagina_categorias.dart';
import '../perfil/pagina_perfil.dart';
import 'pagina_tienda.dart';

// Variable global para saber qué categoría se seleccionó
final ValueNotifier<dynamic> categoriaSeleccionadaNotifier = ValueNotifier(
  null,
);

class Armazon extends StatefulWidget {
  const Armazon({super.key});

  @override
  State<Armazon> createState() => _ArmazonEstado();
}

class _ArmazonEstado extends State<Armazon> {
  int _indice = 0;

  @override
  void initState() {
    super.initState();
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
      body: IndexedStack(
        index: _indice,
        children: [
          const PaginaTienda(),
          // Aquí escuchamos si se seleccionó una categoría
          ValueListenableBuilder(
            valueListenable: categoriaSeleccionadaNotifier,
            builder: (context, categoria, _) {
              if (categoria != null) {
                // Si es una Categoria, se la pasamos en 'categoria'
                // Si es un String (marca), se lo pasamos en 'marca'
                if (categoria is String) {
                  return PaginaCategoriaProductos(marca: categoria);
                } else {
                  return PaginaCategoriaProductos(categoria: categoria);
                }
              }
              return const PaginaCategorias();
            },
          ),
          const PaginaCarrito(),
          const PaginaPerfil(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _indice,
        onDestinationSelected: (i) {
          setState(() {
            _indice = i;
            // Si cambiamos de pestaña, limpiamos la categoría
            if (i != 1) categoriaSeleccionadaNotifier.value = null;
          });
        },
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home, color: Paleta.verde),
            label: 'Inicio',
          ),
          const NavigationDestination(
            icon: Icon(Icons.grid_view_outlined),
            selectedIcon: Icon(Icons.grid_view, color: Paleta.verde),
            label: 'Categorías',
          ),
          NavigationDestination(
            icon: Badge(
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
            label: autenticado ? 'Perfil' : 'Entrar',
          ),
        ],
      ),
    );
  }
}
