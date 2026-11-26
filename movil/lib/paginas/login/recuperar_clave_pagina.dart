// lib/paginas/login/recuperar_clave_pagina.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../nucleo/servicios/sesion.dart';

/// Recuperar la contraseña en dos pasos: pedir un código por correo y
/// escribirlo junto con la contraseña nueva.
///
/// Al terminar devuelve el correo con `Navigator.pop`, para que el login lo
/// deje escrito y solo falte la contraseña nueva.
class RecuperarClavePagina extends StatefulWidget {
  const RecuperarClavePagina({super.key, this.emailInicial});

  /// Lo que ya hubiera escrito en el login, para no pedirlo dos veces.
  final String? emailInicial;

  @override
  State<RecuperarClavePagina> createState() => _RecuperarClavePaginaState();
}

class _RecuperarClavePaginaState extends State<RecuperarClavePagina> {
  final _formCorreo = GlobalKey<FormState>();
  final _formCodigo = GlobalKey<FormState>();
  late final _email = TextEditingController(text: widget.emailInicial ?? '');
  final _codigo = TextEditingController();
  final _clave = TextEditingController();
  final _confirmacion = TextEditingController();

  bool _codigoPedido = false;
  bool _enviando = false;
  bool _ocultarClave = true;
  String? _error;

  static const Color colorVerde = Color(0xFF00FF7F);
  static const Color colorVerdeOscuro = Color(0xFF008F4C);
  static const Color colorFondo = Color(0xFFF8F9FA);
  static const Color colorGrisTexto = Color(0xFF6C757D);
  static const Color colorRojo = Color(0xFFD32F2F);

  @override
  void dispose() {
    _email.dispose();
    _codigo.dispose();
    _clave.dispose();
    _confirmacion.dispose();
    super.dispose();
  }

  Future<void> _pedirCodigo() async {
    if (!_formCorreo.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _enviando = true;
      _error = null;
    });

    final error = await context.read<Sesion>().solicitarRecuperacion(
      _email.text,
    );
    if (!mounted) return;

    setState(() {
      _enviando = false;
      if (error != null) {
        _error = error.mensajeCompleto;
      } else {
        _codigoPedido = true;
        _codigo.clear();
      }
    });
  }

  Future<void> _cambiarClave() async {
    if (!_formCodigo.currentState!.validate()) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _enviando = true;
      _error = null;
    });

    final error = await context.read<Sesion>().restablecerClave(
      email: _email.text,
      codigo: _codigo.text,
      nuevaClave: _clave.text,
    );
    if (!mounted) return;

    if (error != null) {
      setState(() {
        _enviando = false;
        _error = error.mensajeCompleto;
      });
      return;
    }

    // El mensajero es el de la app, así que el aviso sigue visible en el
    // login al que se vuelve.
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Contraseña cambiada. Entra con la nueva.'),
        behavior: SnackBarBehavior.floating,
      ),
    );
    Navigator.pop(context, _email.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: colorFondo,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: Colors.black, size: 20),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          "Recuperar contraseña",
          style: TextStyle(
            color: Colors.black,
            fontWeight: FontWeight.bold,
            fontSize: 16,
          ),
        ),
        centerTitle: true,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const SizedBox(height: 20),
              Container(
                width: 100,
                height: 100,
                decoration: const BoxDecoration(
                  color: Color(0xFFE0F7E9),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  _codigoPedido ? Icons.mark_email_read_outlined : Icons.key,
                  size: 50,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                _codigoPedido
                    ? "Revisa tu correo"
                    : "¿Olvidaste tu contraseña?",
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                _codigoPedido
                    // "Si hay una cuenta": el servidor no dice si existe, y la
                    // pantalla no puede prometer un correo que quizá no sale.
                    ? "Si hay una cuenta con ${_email.text.trim()}, te enviamos "
                          "un código de 6 dígitos. Caduca en 15 minutos."
                    : "Escribe tu correo y te enviaremos un código para crear "
                          "una contraseña nueva.",
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 14,
                  color: colorGrisTexto,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 32),

              if (_error != null) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFDECEA),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    _error!,
                    style: const TextStyle(color: colorRojo, fontSize: 13),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              if (!_codigoPedido) _pasoCorreo() else _pasoCodigo(),

              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    "¿La recordaste? ",
                    style: TextStyle(fontSize: 13, color: colorGrisTexto),
                  ),
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: const Text(
                      "Volver a iniciar sesión",
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: colorVerdeOscuro,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _pasoCorreo() {
    return Form(
      key: _formCorreo,
      child: Column(
        children: [
          _Campo(
            control: _email,
            etiqueta: 'Correo electrónico',
            hint: 'jorge.morales@gmail.com',
            icono: Icons.mail_outline,
            teclado: TextInputType.emailAddress,
            validar: (v) {
              final t = (v ?? '').trim();
              if (t.isEmpty) return 'Escribe tu correo';
              if (!t.contains('@') || !t.contains('.')) {
                return 'Ese correo no parece válido';
              }
              return null;
            },
          ),
          const SizedBox(height: 24),
          _BotonPrincipal(
            texto: 'Enviar código',
            icono: Icons.send,
            cargando: _enviando,
            alPulsar: _pedirCodigo,
          ),
          const SizedBox(height: 8),
          // Para quien ya recibió el correo y volvió a abrir la app: pedir otro
          // anularía el que tiene en el buzón.
          TextButton(
            onPressed: _enviando
                ? null
                : () {
                    if (!_formCorreo.currentState!.validate()) return;
                    setState(() {
                      _codigoPedido = true;
                      _error = null;
                    });
                  },
            child: const Text(
              'Ya tengo un código',
              style: TextStyle(
                color: colorVerdeOscuro,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _pasoCodigo() {
    return Form(
      key: _formCodigo,
      child: Column(
        children: [
          _Campo(
            control: _codigo,
            etiqueta: 'Código de 6 dígitos',
            hint: '123456',
            icono: Icons.pin_outlined,
            teclado: TextInputType.number,
            formateadores: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            validar: (v) => RegExp(r'^\d{6}$').hasMatch((v ?? '').trim())
                ? null
                : 'El código tiene 6 dígitos',
          ),
          const SizedBox(height: 16),
          _Campo(
            control: _clave,
            etiqueta: 'Contraseña nueva',
            hint: '••••••••',
            icono: Icons.lock_outline,
            oculto: _ocultarClave,
            sufijo: IconButton(
              icon: Icon(
                _ocultarClave
                    ? Icons.visibility_outlined
                    : Icons.visibility_off_outlined,
              ),
              onPressed: () => setState(() => _ocultarClave = !_ocultarClave),
            ),
            // La misma regla que el registro y que el servidor.
            validar: (v) {
              final c = v ?? '';
              if (c.length < 8) return 'Mínimo 8 caracteres';
              if (!c.contains(RegExp(r'[a-z]'))) return 'Falta una minúscula';
              if (!c.contains(RegExp(r'[A-Z]'))) return 'Falta una mayúscula';
              if (!c.contains(RegExp(r'[0-9]'))) return 'Falta un número';
              if (!c.contains(RegExp(r'[@$!%*?&]'))) {
                return 'Falta un símbolo (@, \$, !, %, *, ?, &)';
              }
              return null;
            },
          ),
          const SizedBox(height: 16),
          _Campo(
            control: _confirmacion,
            etiqueta: 'Repite la contraseña',
            hint: '••••••••',
            icono: Icons.lock_outline,
            oculto: _ocultarClave,
            validar: (v) =>
                v == _clave.text ? null : 'Las contraseñas no coinciden',
          ),
          const SizedBox(height: 24),
          _BotonPrincipal(
            texto: 'Cambiar contraseña',
            icono: Icons.check,
            cargando: _enviando,
            alPulsar: _cambiarClave,
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              TextButton(
                onPressed: _enviando
                    ? null
                    : () => setState(() {
                        _codigoPedido = false;
                        _error = null;
                      }),
                child: const Text(
                  'Cambiar correo',
                  style: TextStyle(color: colorGrisTexto),
                ),
              ),
              TextButton(
                onPressed: _enviando ? null : _pedirCodigoDeNuevo,
                child: const Text(
                  'Reenviar código',
                  style: TextStyle(
                    color: colorVerdeOscuro,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _pedirCodigoDeNuevo() async {
    setState(() {
      _enviando = true;
      _error = null;
    });
    final error = await context.read<Sesion>().solicitarRecuperacion(
      _email.text,
    );
    if (!mounted) return;
    setState(() => _enviando = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          error?.mensajeCompleto ??
              'Te enviamos otro código. Solo vale el último.',
        ),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}

class _Campo extends StatelessWidget {
  const _Campo({
    required this.control,
    required this.etiqueta,
    required this.hint,
    required this.icono,
    this.validar,
    this.teclado,
    this.oculto = false,
    this.sufijo,
    this.formateadores,
  });

  final TextEditingController control;
  final String etiqueta;
  final String hint;
  final IconData icono;
  final String? Function(String?)? validar;
  final TextInputType? teclado;
  final bool oculto;
  final Widget? sufijo;
  final List<TextInputFormatter>? formateadores;

  @override
  Widget build(BuildContext context) {
    OutlineInputBorder borde(Color color, [double ancho = 1]) =>
        OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: color, width: ancho),
        );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          etiqueta,
          style: const TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 13,
            color: Colors.black87,
          ),
        ),
        const SizedBox(height: 8),
        TextFormField(
          controller: control,
          keyboardType: teclado,
          obscureText: oculto,
          inputFormatters: formateadores,
          validator: validar,
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: const TextStyle(color: Colors.black38, fontSize: 14),
            filled: true,
            fillColor: Colors.white,
            prefixIcon: Icon(icono, color: Colors.black45, size: 20),
            suffixIcon: sufijo,
            contentPadding: const EdgeInsets.symmetric(vertical: 16),
            border: borde(Colors.black12),
            enabledBorder: borde(Colors.black12),
            focusedBorder: borde(Colors.black, 2),
          ),
        ),
      ],
    );
  }
}

class _BotonPrincipal extends StatelessWidget {
  const _BotonPrincipal({
    required this.texto,
    required this.icono,
    required this.cargando,
    required this.alPulsar,
  });

  final String texto;
  final IconData icono;
  final bool cargando;
  final VoidCallback alPulsar;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: cargando ? null : alPulsar,
        style: ElevatedButton.styleFrom(
          backgroundColor: _RecuperarClavePaginaState.colorVerde,
          foregroundColor: Colors.black,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
        child: cargando
            ? const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.black,
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icono, size: 18),
                  const SizedBox(width: 8),
                  Text(
                    texto,
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
