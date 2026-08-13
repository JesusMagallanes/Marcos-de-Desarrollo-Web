import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/carrito.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../login/pagina_login.dart';

/// El carrito del usuario, el mismo que en la web: vive en el servidor, atado
/// al `uid` del token. Lo que se añada aquí aparece en la web y al revés.
class PaginaCarrito extends StatelessWidget {
  const PaginaCarrito({super.key});

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<Sesion>();

    if (!sesion.autenticado) {
      return Scaffold(
        appBar: AppBar(title: const Text('Carrito')),
        body: _SinSesion(),
      );
    }

    final servicio = context.watch<CarritoServicio>();
    final carrito = servicio.carrito;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Carrito'),
        actions: [
          if (!carrito.estaVacio)
            TextButton(
              onPressed: () => _confirmarVaciado(context),
              child: const Text('Vaciar'),
            ),
        ],
      ),
      body: RefreshIndicator(
        color: Paleta.verde,
        onRefresh: () => context.read<CarritoServicio>().cargar(),
        child: carrito.estaVacio
            ? const _CarritoVacio()
            : ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: carrito.items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (_, i) => _LineaCarrito(item: carrito.items[i]),
              ),
      ),
      bottomNavigationBar: carrito.estaVacio ? null : _ResumenPago(carrito: carrito),
    );
  }

  Future<void> _confirmarVaciado(BuildContext context) async {
    final confirmado = await showDialog<bool>(
      context: context,
      builder: (dialogo) => AlertDialog(
        title: const Text('¿Vaciar el carrito?'),
        content: const Text('Se quitarán todos los productos.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogo, false),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogo, true),
            style: TextButton.styleFrom(foregroundColor: Paleta.rojo),
            child: const Text('Vaciar'),
          ),
        ],
      ),
    );

    if (confirmado == true && context.mounted) {
      await context.read<CarritoServicio>().vaciar();
    }
  }
}

class _LineaCarrito extends StatelessWidget {
  const _LineaCarrito({required this.item});
  final ItemCarrito item;

  @override
  Widget build(BuildContext context) {
    final servicio = context.read<CarritoServicio>();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SizedBox(
                width: 72,
                height: 72,
                child: ImagenProducto(url: item.imagen),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.nombre,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    soles(item.precio),
                    style: const TextStyle(color: Paleta.textoSuave, fontSize: 13),
                  ),

                  // El backend manda el stock disponible en cada línea: si el
                  // producto se agotó desde que se añadió, se avisa aquí en vez
                  // de dejar que reviente al pagar.
                  if (item.excedeStock)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        'Solo quedan ${item.stockDisponible}',
                        style: const TextStyle(
                          color: Paleta.rojo,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),

                  const SizedBox(height: 8),
                  Row(
                    children: [
                      _BotonCantidad(
                        icono: Icons.remove,
                        activo: item.cantidad > 1,
                        alPulsar: () => servicio.cambiarCantidad(item.itemId, item.cantidad - 1),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text(
                          '${item.cantidad}',
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      _BotonCantidad(
                        icono: Icons.add,
                        activo: item.cantidad < item.stockDisponible,
                        alPulsar: () => servicio.cambiarCantidad(item.itemId, item.cantidad + 1),
                      ),
                      const Spacer(),
                      IconButton(
                        onPressed: () => servicio.quitar(item.itemId),
                        icon: const Icon(Icons.delete_outline, size: 20),
                        color: Paleta.rojo,
                        visualDensity: VisualDensity.compact,
                      ),
                    ],
                  ),
                ],
              ),
            ),
            Text(
              soles(item.total),
              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}

class _BotonCantidad extends StatelessWidget {
  const _BotonCantidad({required this.icono, required this.activo, required this.alPulsar});
  final IconData icono;
  final bool activo;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: activo ? alPulsar : null,
        borderRadius: BorderRadius.circular(6),
        child: Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            border: Border.all(color: activo ? Paleta.borde : Paleta.borde.withValues(alpha: 0.5)),
            borderRadius: BorderRadius.circular(6),
          ),
          child: Icon(icono, size: 16, color: activo ? Paleta.texto : Paleta.textoTenue),
        ),
      );
}

class _ResumenPago extends StatelessWidget {
  const _ResumenPago({required this.carrito});
  final Carrito carrito;

  @override
  Widget build(BuildContext context) => SafeArea(
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: Paleta.borde)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Subtotal (${carrito.unidades} art.)',
                    style: const TextStyle(color: Paleta.textoSuave),
                  ),
                  Text(
                    soles(carrito.subtotal),
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  // El checkout con pasarela es el siguiente paso: implica la
                  // saga de MercadoPago y su retorno, que merece su propia
                  // pantalla. Se avisa en vez de dejar un botón que no hace nada.
                  onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('El pago desde la app llega en la próxima entrega.'),
                      behavior: SnackBarBehavior.floating,
                    ),
                  ),
                  child: const Text('Continuar con la compra'),
                ),
              ),
            ],
          ),
        ),
      );
}

class _CarritoVacio extends StatelessWidget {
  const _CarritoVacio();

  @override
  Widget build(BuildContext context) => ListView(
        children: const [
          SizedBox(height: 100),
          Icon(Icons.shopping_cart_outlined, size: 64, color: Paleta.textoTenue),
          SizedBox(height: 16),
          Center(
            child: Text(
              'Tu carrito está vacío',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Paleta.texto),
            ),
          ),
          SizedBox(height: 6),
          Center(
            child: Text(
              'Explora la tienda y añade lo que te guste.',
              style: TextStyle(color: Paleta.textoSuave),
            ),
          ),
        ],
      );
}

class _SinSesion extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.lock_outline, size: 56, color: Paleta.textoTenue),
              const SizedBox(height: 16),
              const Text(
                'Entra para ver tu carrito',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 6),
              const Text(
                'Tu carrito se guarda en tu cuenta, así que lo tienes igual en la web y aquí.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Paleta.textoSuave, height: 1.5),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const PaginaLogin()),
                ),
                child: const Text('Iniciar sesión'),
              ),
            ],
          ),
        ),
      );
}
