import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../nucleo/config/tema.dart';
import '../nucleo/servicios/favoritos_servicio.dart';
import '../nucleo/servicios/sesion.dart';
import '../paginas/login/pagina_login.dart';

class BotonFavorito extends StatelessWidget {
  const BotonFavorito({
    super.key,
    required this.productoId,
    this.size = 16,
    this.padding = const EdgeInsets.all(4),
  });

  final int productoId;
  final double size;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final esFavorito =
        context.watch<FavoritosServicio>().esFavorito(productoId);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: () => _alternar(context),
        child: Container(
          padding: padding,
          decoration: const BoxDecoration(
            color: Colors.white,
            shape: BoxShape.circle,
          ),
          child: Icon(
            esFavorito ? Icons.favorite : Icons.favorite_border,
            size: size,
            color: esFavorito ? Paleta.rojo : Paleta.textoTenue,
          ),
        ),
      ),
    );
  }

  Future<void> _alternar(BuildContext context) async {
    if (!context.read<Sesion>().autenticado) {
      final entro = await Navigator.of(context).push<bool>(
        MaterialPageRoute(
          builder: (_) => const PaginaLogin(
            motivo: 'Entra para guardar tus favoritos',
          ),
        ),
      );
      if (entro != true || !context.mounted) return;
      await context.read<FavoritosServicio>().recargar();
      if (!context.mounted) return;
    }

    final servicio = context.read<FavoritosServicio>();
    final guardado = await servicio.alternar(productoId);
    if (!context.mounted || guardado) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          servicio.error ?? 'No pudimos actualizar tus favoritos.',
        ),
        backgroundColor: Paleta.rojo,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}
