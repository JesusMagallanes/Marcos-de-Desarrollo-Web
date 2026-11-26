import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/entorno.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/estado/navegacion.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/favoritos_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/aviso_pendiente.dart';
import '../direcciones/pagina_direcciones.dart';
import '../login/pagina_login.dart';
import '../pedidos/pagina_pedidos.dart';

/// Mi cuenta. Sin sesión enseña el acceso; con sesión, los datos del usuario y
/// los atajos a las pantallas ya integradas con el backend.
class PaginaPerfil extends StatelessWidget {
  const PaginaPerfil({super.key});

  @override
  Widget build(BuildContext context) {
    final sesion = context.watch<Sesion>();
    final usuario = sesion.usuario;

    if (usuario == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Mi cuenta')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 72,
                  height: 72,
                  decoration: const BoxDecoration(
                    color: Paleta.verdeSuave,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.person_outline,
                      size: 36, color: Paleta.verde),
                ),
                const SizedBox(height: 20),
                const Text(
                  'Entra en tu cuenta',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Usa la misma cuenta de la tienda web para ver tus pedidos y tu carrito.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Paleta.textoSuave, height: 1.5),
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const PaginaLogin()),
                    ),
                    child: const Text('Iniciar sesión'),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final favoritos = context.watch<FavoritosServicio>().cantidad;
    final direccion = usuario.direccionDetallada?.enUnaLinea ?? usuario.direccion;

    return Scaffold(
      backgroundColor: const Color(0xFFF8F8F8),
      appBar: AppBar(title: const Text('Mi cuenta')),
      body: ListView(
        children: [
          Container(
            color: Colors.white,
            padding: const EdgeInsets.all(20),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 30,
                  backgroundColor: Paleta.verdeSuave,
                  child: Text(
                    usuario.iniciales,
                    style: const TextStyle(
                      color: Paleta.verde,
                      fontWeight: FontWeight.w800,
                      fontSize: 20,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        usuario.nombreCompleto,
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        usuario.email,
                        style: const TextStyle(
                          color: Paleta.textoSuave,
                          fontSize: 13,
                        ),
                      ),
                      if (usuario.esStaff) ...[
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: Paleta.verdeSuave,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            usuario.rol,
                            style: const TextStyle(
                              color: Paleta.verde,
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),
          Container(
            color: Colors.white,
            child: Column(
              children: [
                _Dato(
                  icono: Icons.phone_outlined,
                  etiqueta: 'Teléfono',
                  valor: usuario.telefono,
                ),
                const Divider(height: 1),
                _Dato(
                  icono: Icons.home_outlined,
                  etiqueta: 'Dirección',
                  valor: direccion,
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),
          Container(
            color: Colors.white,
            child: Column(
              children: [
                _Enlace(
                  icono: Icons.receipt_long_outlined,
                  etiqueta: 'Mis pedidos',
                  subtitulo: 'Consulta tus compras y su estado real',
                  alPulsar: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const PaginaPedidos(),
                    ),
                  ),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.edit_location_outlined,
                  etiqueta: 'Dirección de entrega',
                  subtitulo: direccion.isEmpty
                      ? 'Todavía no registras una dirección'
                      : direccion,
                  alPulsar: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const PaginaDirecciones(),
                    ),
                  ),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.favorite_border,
                  etiqueta: 'Mis favoritos',
                  subtitulo: favoritos == 0
                      ? 'Sin productos guardados'
                      : '$favoritos producto${favoritos == 1 ? '' : 's'} guardado${favoritos == 1 ? '' : 's'}',
                  alPulsar: () =>
                      favoritosDesdeNotifier.value = pestanaActivaNotifier.value,
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),
          Container(
            color: Colors.white,
            child: Column(
              children: [
                _Enlace(
                  icono: Icons.notifications_none_outlined,
                  etiqueta: 'Notificaciones',
                  subtitulo: 'Módulo a cargo de otra persona del equipo',
                  alPulsar: () =>
                      mostrarModuloPendiente(context, 'Notificaciones'),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.credit_card_outlined,
                  etiqueta: 'Métodos de pago guardados',
                  subtitulo: 'Requiere tokenización del proveedor',
                  alPulsar: () =>
                      mostrarModuloPendiente(context, 'Métodos guardados'),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.smart_toy_outlined,
                  etiqueta: 'Asesor IA',
                  subtitulo: 'Sin cambios en esta entrega',
                  alPulsar: () => mostrarModuloPendiente(context, 'Asesor IA'),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.support_agent_outlined,
                  etiqueta: 'Soporte',
                  subtitulo: 'Sin canal oficial configurado',
                  alPulsar: () => mostrarModuloPendiente(context, 'Soporte'),
                ),
                const Divider(height: 1),
                _Enlace(
                  icono: Icons.settings_outlined,
                  etiqueta: 'Ajustes',
                  subtitulo: 'Sin opciones disponibles todavía',
                  alPulsar: () => mostrarModuloPendiente(context, 'Ajustes'),
                ),
              ],
            ),
          ),

          const SizedBox(height: 12),
          Container(
            color: Colors.white,
            child: ListTile(
              leading: const Icon(Icons.logout, color: Paleta.rojo),
              title: const Text(
                'Cerrar sesión',
                style: TextStyle(color: Paleta.rojo),
              ),
              onTap: () async {
                await context.read<Sesion>().salir();
                if (context.mounted) context.read<CarritoServicio>().olvidar();
              },
            ),
          ),

          // En desarrollo importa saber contra qué backend se está hablando:
          // el fallo más común es apuntar al sitio equivocado, no un error de
          // código. En producción no aparece.
          if (Entorno.esEntornoLocal)
            Padding(
              padding: const EdgeInsets.all(20),
              child: Text(
                'Conectado a ${Entorno.apiUrl}',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 11, color: Paleta.textoTenue),
              ),
            ),
        ],
      ),
    );
  }
}

class _Dato extends StatelessWidget {
  const _Dato({required this.icono, required this.etiqueta, required this.valor});
  final IconData icono;
  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) => ListTile(
        leading: Icon(icono, color: Paleta.textoTenue, size: 20),
        title: Text(
          etiqueta,
          style: const TextStyle(fontSize: 12, color: Paleta.textoTenue),
        ),
        subtitle: Text(
          valor.isEmpty ? '—' : valor,
          style: const TextStyle(fontSize: 14, color: Paleta.texto),
        ),
      );
}

class _Enlace extends StatelessWidget {
  const _Enlace({
    required this.icono,
    required this.etiqueta,
    required this.subtitulo,
    required this.alPulsar,
  });

  final IconData icono;
  final String etiqueta;
  final String subtitulo;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) => ListTile(
        leading: Icon(icono, color: Paleta.verdeOscuro, size: 20),
        title: Text(
          etiqueta,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
            color: Paleta.textoPrimario,
          ),
        ),
        subtitle: Text(
          subtitulo,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 11, color: Paleta.textoSecundario),
        ),
        trailing: const Icon(
          Icons.chevron_right,
          size: 18,
          color: Paleta.textoTenue,
        ),
        onTap: alPulsar,
      );
}
