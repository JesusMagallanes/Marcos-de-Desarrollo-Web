import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/estado/navegacion.dart';
import '../../nucleo/modelos/carrito.dart';
import '../../nucleo/modelos/producto.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/catalogo_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/boton_accion.dart';
import '../../widgets/boton_favorito.dart';
import '../../widgets/cabecera_seccion.dart';
import '../../widgets/chip_estado.dart';
import '../../widgets/control_cantidad.dart';
import '../../widgets/formatos.dart';
import '../../widgets/tarjeta_producto.dart';
import '../chat/chat_pagina.dart';
import '../checkout/pagina_checkout.dart';
import '../login/pagina_login.dart';
import '../producto/pagina_producto.dart';

/// Carrito del usuario: el mismo que en la web, atado al `uid` del token.
class PaginaCarrito extends StatelessWidget {
  const PaginaCarrito({super.key});

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<Sesion>();

    if (!sesion.autenticado) {
      return Scaffold(
        backgroundColor: Paleta.fondoClaro,
        body: Column(
          children: [
            const CabeceraSeccion(titulo: 'Tu carrito'),
            Expanded(child: _SinSesion()),
          ],
        ),
      );
    }

    final servicio = context.watch<CarritoServicio>();
    final carrito = servicio.carrito;
    final error = servicio.error;

    return Scaffold(
      backgroundColor: Paleta.fondoClaro,
      body: Column(
        children: [
          CabeceraSeccion(
            titulo: 'Tu carrito',
            accion: carrito.estaVacio
                ? null
                : Text(
                    '${carrito.unidades} productos',
                    style: const TextStyle(
                      fontSize: 11,
                      color: Paleta.textoSecundario,
                    ),
                  ),
          ),
          if (error != null)
            _AvisoError(
              error: error,
              alReintentar: () => context.read<CarritoServicio>().cargar(),
            ),
          Expanded(
            child: RefreshIndicator(
              color: Paleta.verdeOscuro,
              onRefresh: () => context.read<CarritoServicio>().cargar(),
              child: carrito.estaVacio
                  ? const _CarritoVacio()
                  : ListView.separated(
                      padding: const EdgeInsets.fromLTRB(15, 12, 15, 16),
                      itemCount: carrito.items.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 9),
                      itemBuilder: (_, i) => _LineaCarrito(item: carrito.items[i]),
                    ),
            ),
          ),
          if (!carrito.estaVacio) _PieCarrito(carrito: carrito),
        ],
      ),
    );
  }
}

class _AvisoError extends StatelessWidget {
  const _AvisoError({required this.error, required this.alReintentar});
  final ErrorApi error;
  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        margin: const EdgeInsets.fromLTRB(15, 10, 15, 0),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: const Color(0xFFFFF1F1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Paleta.rojo.withValues(alpha: 0.4)),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline, size: 18, color: Paleta.rojo),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                error.mensaje,
                style: const TextStyle(fontSize: 12, color: Paleta.rojo),
              ),
            ),
            TextButton(onPressed: alReintentar, child: const Text('Reintentar')),
          ],
        ),
      );
}

class _LineaCarrito extends StatelessWidget {
  const _LineaCarrito({required this.item});
  final ItemCarrito item;

  @override
  Widget build(BuildContext context) {
    final servicio = context.read<CarritoServicio>();
    final ocupado = servicio.cargando;
    final tope = item.stockDisponible > 0 ? item.stockDisponible : item.cantidad;

    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Paleta.bordeTarjeta),
      ),
      // IntrinsicHeight da a la fila el alto de su contenido. Sin él, la
      // columna de la derecha (papelera arriba, total abajo con un Spacer)
      // recibía alto infinito dentro de la lista y la línea no se pintaba.
      child: IntrinsicHeight(
        child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(
              width: 64,
              height: 64,
              child: ImagenProducto(url: item.imagen),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.nombre,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  '${soles(item.precio)} c/u',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Paleta.textoSecundario,
                  ),
                ),
                if (item.excedeStock)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      'Solo quedan ${item.stockDisponible}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: Paleta.rojo,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                ControlCantidad(
                  cantidad: item.cantidad,
                  minimo: 1,
                  maximo: tope,
                  ocupado: ocupado,
                  alDisminuir: () => servicio.cambiarCantidad(item.itemId, item.cantidad - 1),
                  alAumentar: () => servicio.cambiarCantidad(item.itemId, item.cantidad + 1),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 78,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                InkWell(
                  onTap: ocupado ? null : () => servicio.quitar(item.itemId),
                  child: const Padding(
                    padding: EdgeInsets.all(4),
                    child: Icon(Icons.delete_outline, size: 20, color: Paleta.rojo),
                  ),
                ),
                const Spacer(),
                Text(
                  soles(item.total),
                  textAlign: TextAlign.right,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ],
            ),
          ),
        ],
        ),
      ),
    );
  }
}

class _PieCarrito extends StatelessWidget {
  const _PieCarrito({required this.carrito});
  final Carrito carrito;

  @override
  Widget build(BuildContext context) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: Paleta.bordeTarjeta)),
        ),
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 12,
          bottom: MediaQuery.paddingOf(context).bottom + 12,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _FilaResumen(
              etiqueta: 'Subtotal (${carrito.unidades} productos)',
              valor: soles(carrito.subtotal),
            ),
            const SizedBox(height: 6),
            _FilaResumen(
              etiqueta: 'Envío a domicilio',
              valor: carrito.envioGratis ? 'GRATIS' : soles(carrito.costoEnvio),
              valorVerde: carrito.envioGratis,
            ),
            if (carrito.cuponCodigo != null && carrito.cuponCodigo!.isNotEmpty) ...[
              const SizedBox(height: 6),
              _FilaResumen(
                etiqueta: 'Cupón ${carrito.cuponCodigo}',
                valor: '- ${soles(carrito.descuento)}',
                valorRojo: true,
              ),
            ],
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 10),
              child: Divider(height: 1),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Total a pagar',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                Text(
                  soles(carrito.total),
                  style: const TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            BotonAccion(
              etiqueta: 'Continuar con la compra',
              icono: Icons.lock_outlined,
              alPulsar: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => PaginaCheckout(carrito: carrito),
                ),
              ),
            ),
          ],
        ),
      );
}

class _FilaResumen extends StatelessWidget {
  const _FilaResumen({
    required this.etiqueta,
    required this.valor,
    this.valorVerde = false,
    this.valorRojo = false,
  });

  final String etiqueta;
  final String valor;
  final bool valorVerde;
  final bool valorRojo;

  @override
  Widget build(BuildContext context) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              etiqueta,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, color: Paleta.textoSecundario),
            ),
          ),
          Text(
            valor,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: valorVerde
                  ? Paleta.verdeOscuro
                  : valorRojo
                      ? Paleta.rojo
                      : Paleta.textoPrimario,
            ),
          ),
        ],
      );
}

class _CarritoVacio extends StatelessWidget {
  const _CarritoVacio();

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        children: [
          const SizedBox(height: 34),
          Center(
            child: Container(
              width: 128,
              height: 128,
              decoration: const BoxDecoration(
                color: Paleta.verdeSuave,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.shopping_cart_outlined,
                size: 52,
                color: Paleta.textoPrimario,
              ),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Tu carrito está vacío',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w800,
              color: Paleta.textoPrimario,
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Explora el catálogo o deja que el asesor IA te recomiende el equipo ideal.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 12.5,
              height: 1.45,
              color: Paleta.textoSecundario,
            ),
          ),
          const SizedBox(height: 22),
          BotonAccion(
            etiqueta: 'Explorar catálogo',
            alPulsar: () {
              pestanaActivaNotifier.value = 0;
              Navigator.of(context).maybePop();
            },
          ),
          const SizedBox(height: 9),
          BotonAccion(
            etiqueta: 'Hablar con el asesor IA',
            icono: Icons.smart_toy_outlined,
            verde: false,
            alPulsar: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const ChatPagina()),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Te puede interesar',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: Paleta.textoPrimario,
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            height: 196,
            child: FutureBuilder<List<Producto>>(
              future: context.read<CatalogoServicio>().productos(),
              builder: (context, estado) {
                if (estado.connectionState == ConnectionState.waiting) {
                  return const Center(
                    child: CircularProgressIndicator(strokeWidth: 2),
                  );
                }
                if (estado.hasError || estado.data == null || estado.data!.isEmpty) {
                  return const Center(
                    child: Text(
                      'No hay productos disponibles en este momento.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: Paleta.textoSecundario),
                    ),
                  );
                }
                return ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: estado.data!.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 10),
                  itemBuilder: (_, i) => _Sugerencia(producto: estado.data![i]),
                );
              },
            ),
          ),
          const SizedBox(height: 16),
        ],
      );
}

class _Sugerencia extends StatelessWidget {
  const _Sugerencia({required this.producto});
  final Producto producto;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: 138,
        child: GestureDetector(
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => PaginaProducto(productoId: producto.id),
            ),
          ),
          child: Container(
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: Paleta.bordeTarjeta),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    if (producto.porcentajeDescuento != null)
                      ChipEstado(
                        etiqueta: '-${producto.porcentajeDescuento}%',
                        fondo: Paleta.verdeSuave,
                        colorTexto: Paleta.verdeOscuro,
                      )
                    else if (producto.categoriaNombre.isNotEmpty)
                      ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 92),
                        child: ChipEstado(
                          etiqueta: producto.categoriaNombre,
                          fondo: Paleta.verdeSuave,
                          colorTexto: Paleta.verdeOscuro,
                        ),
                      ),
                    const Spacer(),
                    BotonFavorito(productoId: producto.id, size: 14),
                  ],
                ),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: SizedBox(
                    height: 78,
                    width: double.infinity,
                    child: ImagenProducto(url: producto.imagenUrl),
                  ),
                ),
                const SizedBox(height: 7),
                Text(
                  producto.categoriaNombre,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 10, color: Paleta.textoSecundario),
                ),
                const SizedBox(height: 2),
                Text(
                  producto.nombre,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: Paleta.textoPrimario,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  soles(producto.precioActual),
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w800,
                    color: Paleta.textoPrimario,
                  ),
                ),
              ],
            ),
          ),
        ),
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
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 6),
              const Text(
                'Tu carrito se guarda en tu cuenta, así que lo tienes igual en la web y aquí.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Paleta.textoSecundario, height: 1.5),
              ),
              const SizedBox(height: 20),
              BotonAccion(
                etiqueta: 'Iniciar sesión',
                alPulsar: () => Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const PaginaLogin()),
                ),
              ),
            ],
          ),
        ),
      );
}
