import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';

/// Entrar con una cuenta ya existente, o crear una nueva.
///
/// Es la misma cuenta de la web: las credenciales van contra `usuarios`, y el
/// token que devuelve sirve igual para el carrito y los pedidos. Quien ya
/// compra por la web entra aquí con lo que ya tiene.
class PaginaLogin extends StatefulWidget {
  const PaginaLogin({super.key, this.motivo});

  /// Por qué se está pidiendo entrar ("Entra para añadir al carrito"). Ayuda a
  /// que la pantalla no aparezca sin explicación.
  final String? motivo;

  @override
  State<PaginaLogin> createState() => _PaginaLoginEstado();
}

class _PaginaLoginEstado extends State<PaginaLogin> {
  final _formulario = GlobalKey<FormState>();
  bool _modoRegistro = false;
  bool _ocultarClave = true;
  bool _enviando = false;
  String? _errorGeneral;

  final _email = TextEditingController();
  final _clave = TextEditingController();
  final _nombre = TextEditingController();
  final _apellidos = TextEditingController();
  final _telefono = TextEditingController();
  final _direccion = TextEditingController();

  @override
  void dispose() {
    for (final c in [_email, _clave, _nombre, _apellidos, _telefono, _direccion]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _enviar() async {
    if (!_formulario.currentState!.validate()) return;

    setState(() {
      _enviando = true;
      _errorGeneral = null;
    });

    final sesion = context.read<Sesion>();
    final error = _modoRegistro
        ? await sesion.registrar(
            nombre: _nombre.text,
            apellidos: _apellidos.text,
            email: _email.text,
            password: _clave.text,
            telefono: _telefono.text,
            direccion: _direccion.text,
          )
        : await sesion.entrar(_email.text, _clave.text);

    if (!mounted) return;

    if (error != null) {
      setState(() {
        _enviando = false;
        _errorGeneral = error.mensajeCompleto;
      });
      return;
    }

    // Con sesión nueva, el carrito del servidor pasa a ser el de este usuario.
    await context.read<CarritoServicio>().cargar();
    if (!mounted) return;
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_modoRegistro ? 'Crear cuenta' : 'Iniciar sesión')),
      body: SafeArea(
        child: Form(
          key: _formulario,
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              if (widget.motivo != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Paleta.verdeSuave,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.info_outline, size: 20, color: Paleta.verde),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          widget.motivo!,
                          style: const TextStyle(color: Paleta.verde, fontSize: 13),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
              ],

              if (_errorGeneral != null) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFDECEC),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    _errorGeneral!,
                    style: const TextStyle(color: Paleta.rojo, fontSize: 13),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              if (_modoRegistro) ...[
                _Campo(
                  control: _nombre,
                  etiqueta: 'Nombre',
                  icono: Icons.person_outline,
                  validar: (v) => (v == null || v.trim().length < 2) ? 'Mínimo 2 caracteres' : null,
                ),
                _Campo(
                  control: _apellidos,
                  etiqueta: 'Apellidos',
                  icono: Icons.person_outline,
                  validar: (v) => (v == null || v.trim().length < 2) ? 'Mínimo 2 caracteres' : null,
                ),
              ],

              _Campo(
                control: _email,
                etiqueta: 'Correo electrónico',
                icono: Icons.mail_outline,
                teclado: TextInputType.emailAddress,
                validar: (v) {
                  if (v == null || v.trim().isEmpty) return 'Escribe tu correo';
                  if (!v.contains('@') || !v.contains('.')) return 'Correo no válido';
                  return null;
                },
              ),

              _Campo(
                control: _clave,
                etiqueta: 'Contraseña',
                icono: Icons.lock_outline,
                oculto: _ocultarClave,
                sufijo: IconButton(
                  icon: Icon(_ocultarClave ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                  onPressed: () => setState(() => _ocultarClave = !_ocultarClave),
                ),
                validar: (v) {
                  if (v == null || v.isEmpty) return 'Escribe tu contraseña';
                  // Solo al registrar se exige el patrón completo: al entrar,
                  // validar el formato aquí impediría avisar de la contraseña
                  // equivocada y sería más confuso.
                  if (_modoRegistro && v.length < 8) return 'Mínimo 8 caracteres';
                  return null;
                },
              ),

              if (_modoRegistro) ...[
                _Campo(
                  control: _telefono,
                  etiqueta: 'Teléfono (9 dígitos)',
                  icono: Icons.phone_outlined,
                  teclado: TextInputType.phone,
                  validar: (v) => (v == null || !RegExp(r'^\d{9}$').hasMatch(v.trim()))
                      ? 'Deben ser 9 dígitos'
                      : null,
                ),
                _Campo(
                  control: _direccion,
                  etiqueta: 'Dirección de envío',
                  icono: Icons.home_outlined,
                  validar: (v) => (v == null || v.trim().isEmpty) ? 'Escribe tu dirección' : null,
                ),
                const SizedBox(height: 4),
                const Text(
                  'La contraseña necesita 8 caracteres con mayúscula, minúscula, número y símbolo.',
                  style: TextStyle(fontSize: 12, color: Paleta.textoTenue),
                ),
              ],

              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _enviando ? null : _enviar,
                child: _enviando
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : Text(_modoRegistro ? 'Crear cuenta' : 'Entrar'),
              ),

              const SizedBox(height: 12),
              TextButton(
                onPressed: _enviando
                    ? null
                    : () => setState(() {
                          _modoRegistro = !_modoRegistro;
                          _errorGeneral = null;
                        }),
                child: Text(
                  _modoRegistro
                      ? '¿Ya tienes cuenta? Inicia sesión'
                      : '¿No tienes cuenta? Créala aquí',
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Campo extends StatelessWidget {
  const _Campo({
    required this.control,
    required this.etiqueta,
    required this.icono,
    this.validar,
    this.teclado,
    this.oculto = false,
    this.sufijo,
  });

  final TextEditingController control;
  final String etiqueta;
  final IconData icono;
  final String? Function(String?)? validar;
  final TextInputType? teclado;
  final bool oculto;
  final Widget? sufijo;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: TextFormField(
          controller: control,
          obscureText: oculto,
          keyboardType: teclado,
          validator: validar,
          decoration: InputDecoration(
            labelText: etiqueta,
            prefixIcon: Icon(icono, size: 20),
            suffixIcon: sufijo,
          ),
        ),
      );
}
