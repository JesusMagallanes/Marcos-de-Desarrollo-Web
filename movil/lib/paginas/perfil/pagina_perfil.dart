import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/entorno.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../login/pagina_login.dart';

/// Mi cuenta. Sin sesión enseña el acceso; con sesión, los datos del usuario.
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
                  child: const Icon(Icons.person_outline, size: 36, color: Paleta.verde),
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

    return Scaffold(
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
                        style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        usuario.email,
                        style: const TextStyle(color: Paleta.textoSuave, fontSize: 13),
                      ),
                      if (usuario.esStaff) ...[
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
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
                _Dato(icono: Icons.phone_outlined, etiqueta: 'Teléfono', valor: usuario.telefono),
                const Divider(height: 1),
                _Dato(icono: Icons.home_outlined, etiqueta: 'Dirección', valor: usuario.direccion),
              ],
            ),
          ),

          const SizedBox(height: 12),
          Container(
            color: Colors.white,
            child: ListTile(
              leading: const Icon(Icons.logout, color: Paleta.rojo),
              title: const Text('Cerrar sesión', style: TextStyle(color: Paleta.rojo)),
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
        title: Text(etiqueta, style: const TextStyle(fontSize: 12, color: Paleta.textoTenue)),
        subtitle: Text(
          valor.isEmpty ? '—' : valor,
          style: const TextStyle(fontSize: 14, color: Paleta.texto),
        ),
      );
}
