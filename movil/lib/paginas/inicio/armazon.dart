import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/estado/navegacion.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../carrito/pagina_carrito.dart';
import '../categorias/pagina_categoria_productos.dart'; // <-- IMPORTANTE
import '../categorias/pagina_categorias.dart';
import '../favoritos/pagina_favoritos.dart';
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
  int _indice = pestanaActivaNotifier.value;
  bool _favoritosMontados = false;

  @override
  void initState() {
    super.initState();
    pestanaActivaNotifier.addListener(_sincronizarPestana);
    favoritosDesdeNotifier.addListener(_sincronizarFavoritos);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<Sesion>().autenticado) {
        context.read<CarritoServicio>().cargar();
      }
    });
  }

  @override
  void dispose() {
    pestanaActivaNotifier.removeListener(_sincronizarPestana);
    favoritosDesdeNotifier.removeListener(_sincronizarFavoritos);
    super.dispose();
  }

  void _sincronizarFavoritos() {
    if (favoritosDesdeNotifier.value != null) _favoritosMontados = true;
    if (mounted) setState(() {});
  }

  void _sincronizarPestana() {
    if (!mounted) return;
    setState(() {
      _indice = pestanaActivaNotifier.value;
      if (_indice != 1) categoriaSeleccionadaNotifier.value = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final unidades = context.watch<CarritoServicio>().unidades;
    final autenticado = context.watch<Sesion>().autenticado;

    return ValueListenableBuilder<int?>(
      valueListenable: favoritosDesdeNotifier,
      builder: (context, desdeFavoritos, _) {
        final abierto = desdeFavoritos != null;

        return PopScope<int>(
          canPop: !abierto,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop && abierto) favoritosDesdeNotifier.value = null;
          },
          child: Scaffold(
            body: Stack(
              children: [
                IndexedStack(
                  index: _indice,
                  children: [
                    const PaginaTienda(),
                    ValueListenableBuilder(
                      valueListenable: categoriaSeleccionadaNotifier,
                      builder: (context, categoria, _) {
                        if (categoria != null) {
                          if (categoria is String) {
                            return PaginaCategoriaProductos(marca: categoria);
                          }
                          return PaginaCategoriaProductos(categoria: categoria);
                        }
                        return const PaginaCategorias();
                      },
                    ),
                    const PaginaCarrito(),
                    const PaginaPerfil(),
                  ],
                ),
                if (_favoritosMontados)
                  Offstage(
                    offstage: !abierto,
                    child: const PaginaFavoritos(),
                  ),
              ],
            ),
            bottomNavigationBar: NavigationBar(
              selectedIndex: _indice,
              onDestinationSelected: (i) {
                if (abierto) favoritosDesdeNotifier.value = null;
                pestanaActivaNotifier.value = i;
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
          ),
        );
      },
    );
  }
}
