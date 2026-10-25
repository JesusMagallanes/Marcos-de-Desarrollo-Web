import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/categoria.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../inicio/buscador_widget.dart';
import 'pagina_categoria_productos.dart';

class PaginaCategorias extends StatelessWidget {
  const PaginaCategorias({super.key});

  @override
  Widget build(BuildContext context) {
    final catalogo = context.watch<CatalogoServicio>();
    final categorias = catalogo.categoriasEnCache ?? [];

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      floatingActionButton: FloatingActionButton(
        onPressed: () {},
        backgroundColor: const Color(0xFF00E676),
        child: const Icon(Icons.chat_bubble, color: Colors.black),
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: Paleta.verde,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(
                          Icons.bolt,
                          color: Colors.white,
                          size: 24,
                        ),
                      ),
                      const SizedBox(width: 12),
                      const Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'SmartZone',
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: Paleta.texto,
                            ),
                          ),
                          Text(
                            'Perú • Tecnología & Hardware',
                            style: TextStyle(
                              fontSize: 10,
                              color: Paleta.textoTenue,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      Container(
                        decoration: BoxDecoration(
                          border: Border.all(color: const Color(0xFFEEEEEE)),
                          shape: BoxShape.circle,
                        ),
                        child: IconButton(
                          icon: const Icon(
                            Icons.chat_bubble_outline,
                            size: 20,
                            color: Paleta.texto,
                          ),
                          onPressed: () {},
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        decoration: BoxDecoration(
                          border: Border.all(color: const Color(0xFFEEEEEE)),
                          shape: BoxShape.circle,
                        ),
                        child: IconButton(
                          icon: const Icon(
                            Icons.notifications_none_outlined,
                            size: 20,
                            color: Paleta.texto,
                          ),
                          onPressed: () {},
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: 16.0,
                vertical: 8.0,
              ),
              child: GestureDetector(
                onTap: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const BuscadorWidget()),
                  );
                },
                child: Container(
                  height: 44,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Row(
                    children: [
                      SizedBox(width: 12),
                      Icon(Icons.search, color: Paleta.textoTenue, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Buscar laptops, teclados, monitores...',
                        style: TextStyle(
                          color: Paleta.textoTenue,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

            // Contenido
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Explora por categoría',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: Paleta.texto,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Equipos oficiales con garantía nacional',
                      style: TextStyle(fontSize: 13, color: Paleta.textoTenue),
                    ),
                    const SizedBox(height: 24),

                    GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 2,
                            crossAxisSpacing: 16,
                            mainAxisSpacing: 16,
                            childAspectRatio: 0.95,
                          ),
                      itemCount: categorias.length,
                      itemBuilder: (context, index) {
                        return _TarjetaCategoria(categoria: categorias[index]);
                      },
                    ),

                    const SizedBox(height: 32),
                    const Text(
                      'Marcas oficiales',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: Paleta.texto,
                      ),
                    ),
                    const SizedBox(height: 12),

                    // MARCAS CLICABLES
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: ['ASUS', 'Lenovo', 'HP', 'Dell', 'Logitech']
                          .map((marca) {
                            return GestureDetector(
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) =>
                                        PaginaCategoriaProductos(marca: marca),
                                  ),
                                );
                              },
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 16,
                                  vertical: 8,
                                ),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(8),
                                  border: Border.all(
                                    color: const Color(0xFFEEEEEE),
                                  ),
                                ),
                                child: Text(
                                  marca,
                                  style: const TextStyle(
                                    fontSize: 13,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            );
                          })
                          .toList(),
                    ),
                    const SizedBox(height: 80),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TarjetaCategoria extends StatelessWidget {
  const _TarjetaCategoria({required this.categoria});
  final Categoria categoria;

  IconData _obtenerIcono(String nombre) {
    final n = nombre.toLowerCase();
    if (n.contains('laptop') || n.contains('mac')) return Icons.laptop;
    if (n.contains('pc') || n.contains('ensamblada'))
      return Icons.desktop_windows;
    if (n.contains('monitor') || n.contains('pantalla')) return Icons.monitor;
    if (n.contains('teclado') || n.contains('mouse')) return Icons.mouse;
    if (n.contains('ssd') || n.contains('disco')) return Icons.sd_storage;
    if (n.contains('silla') || n.contains('gamer')) return Icons.chair;
    return Icons.devices_other;
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => PaginaCategoriaProductos(categoria: categoria),
          ),
        );
      },
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFEEEEEE)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: const BoxDecoration(
                  color: Color(0xFFF3F4F6),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  _obtenerIcono(categoria.nombre),
                  color: Paleta.texto,
                  size: 28,
                ),
              ),
              const Spacer(),
              Text(
                categoria.nombre,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Paleta.texto,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 4),
              const Text(
                'Productos oficiales',
                style: TextStyle(fontSize: 11, color: Paleta.textoTenue),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
