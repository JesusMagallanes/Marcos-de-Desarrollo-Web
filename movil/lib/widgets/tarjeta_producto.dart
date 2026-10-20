import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../nucleo/config/entorno.dart';
import '../nucleo/config/tema.dart';
import '../nucleo/modelos/producto.dart';
import 'formatos.dart';

/// Tarjeta de producto del listado. Mismo contenido que la de la web: imagen,
/// nombre, precio y el aviso de sin stock.
class TarjetaProducto extends StatelessWidget {
  const TarjetaProducto({
    super.key,
    required this.producto,
    required this.alPulsar,
  });

  final Producto producto;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: alPulsar,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                AspectRatio(
                  aspectRatio: 1,
                  child: ImagenProducto(url: producto.imagenUrl),
                ),
                if (producto.porcentajeDescuento != null)
                  Positioned(
                    top: 8,
                    left: 8,
                    child: _Etiqueta(
                      texto: '-${producto.porcentajeDescuento}%',
                      color: Paleta.rojo,
                    ),
                  ),
                if (producto.sinStock)
                  Positioned(
                    top: 8,
                    right: 8,
                    child: _Etiqueta(
                      texto: 'Sin stock',
                      color: Paleta.grisOscuro,
                    ),
                  ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    producto.nombre,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: Paleta.texto,
                      height: 1.3,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    soles(producto.precioActual),
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w800,
                      color: Paleta.texto,
                    ),
                  ),
                  // El precio tachado solo tiene sentido si de verdad es mayor.
                  if (producto.enOferta &&
                      producto.precio > producto.precioActual)
                    Text(
                      soles(producto.precio),
                      style: const TextStyle(
                        fontSize: 12,
                        color: Paleta.textoTenue,
                        decoration: TextDecoration.lineThrough,
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

/// Imagen de producto con respaldo.
///
/// Las URL del catálogo pueden ser:
///
/// - Absolutas (`https://…`): se usan tal cual.
/// - Relativas al backend (`/api/…`, `/archivos/…`): se completan con la
///   dirección del backend antes de pedirlas.
/// - El placeholder histórico `/Img/img.png`: apuntaba a un asset del antiguo
///   frontend Angular, no a un recurso del catálogo. Con el gateway delante
///   esa ruta da 500; sin él, 401. Mejor no pedirla y mostrar el respaldo
///   local (el icono de "sin imagen").
class ImagenProducto extends StatelessWidget {
  const ImagenProducto({
    super.key,
    required this.url,
    this.ajuste = BoxFit.contain,
  });

  final String? url;
  final BoxFit ajuste;

  @override
  Widget build(BuildContext context) {
    final limpia = url?.trim() ?? '';

    // Vacío o el placeholder heredado de la web antigua: no sirve para el
    // móvil, se cae al respaldo local.
    if (limpia.isEmpty || limpia.startsWith('/Img/')) {
      return const _SinImagen();
    }

    final absoluta = limpia.startsWith('http')
        ? limpia
        : '${Entorno.apiUrl}$limpia';

    return CachedNetworkImage(
      imageUrl: absoluta,
      fit: ajuste,
      placeholder: (_, _) => const ColoredBox(
        color: Colors.white,
        child: Center(
          child: SizedBox(
            width: 22,
            height: 22,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      ),
      errorWidget: (_, _, _) => const _SinImagen(),
    );
  }
}

class _SinImagen extends StatelessWidget {
  const _SinImagen();

  @override
  Widget build(BuildContext context) => const ColoredBox(
    color: Color(0xFFF7F8F7),
    child: Center(
      child: Icon(Icons.image_not_supported_outlined, color: Paleta.textoTenue),
    ),
  );
}

class _Etiqueta extends StatelessWidget {
  const _Etiqueta({required this.texto, required this.color});
  final String texto;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(6),
    ),
    child: Text(
      texto,
      style: const TextStyle(
        color: Colors.white,
        fontSize: 11,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}
