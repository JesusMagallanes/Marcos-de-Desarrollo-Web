import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../nucleo/config/tema.dart';
import '../../nucleo/servicios/carrito_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../arranque.dart';

const Color colorRojoError = Color(0xFFD32F2F);

class PaginaLogin extends StatefulWidget {
  const PaginaLogin({super.key, this.motivo});

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

  bool _recordarme = true;
  bool _aceptaTerminos = false;

  // Controladores
  final _email = TextEditingController();
  final _clave = TextEditingController();
  final _nombreCompleto = TextEditingController();
  final _telefono = TextEditingController();
  final _direccion = TextEditingController();

  // Colores del Figma
  static const Color colorVerde = Color(0xFF00FF7F);
  static const Color colorFondo = Color(0xFFF8F9FA);
  static const Color colorGrisInput = Color(0xFFF1F3F5);
  static const Color colorGrisTexto = Color(0xFF6C757D);

  @override
  void dispose() {
    for (final c in [_email, _clave, _nombreCompleto, _telefono, _direccion]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _enviar() async {
    if (!_formulario.currentState!.validate()) return;

    if (_modoRegistro && !_aceptaTerminos) {
      setState(() {
        _errorGeneral =
            "Debes aceptar los términos y la política de privacidad.";
      });
      return;
    }

    setState(() {
      _enviando = true;
      _errorGeneral = null;
    });

    final sesion = context.read<Sesion>();

    final partesNombre = _nombreCompleto.text.trim().split(' ');
    final nombre = partesNombre.first;
    final apellidos = partesNombre.length > 1
        ? partesNombre.sublist(1).join(' ')
        : '';

    final error = _modoRegistro
        ? await sesion.registrar(
            nombre: nombre,
            apellidos: apellidos,
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

    await context.read<CarritoServicio>().cargar();
    if (!mounted) return;

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => const Arranque()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: colorFondo,
      body: SafeArea(
        child: Form(
          key: _formulario,
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            children: [
              // Logo
              Center(
                child: Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: colorVerde,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(Icons.bolt, size: 40, color: Colors.black),
                ),
              ),
              const SizedBox(height: 24),

              // Títulos
              const Text(
                "Acceso a SmartZone",
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                "Precios exclusivos y seguimiento de pedidos en vivo",
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, color: colorGrisTexto),
              ),
              const SizedBox(height: 32),

              // Pestañas (Iniciar Sesión / Crear Cuenta)
              Container(
                height: 48,
                decoration: BoxDecoration(
                  color: colorGrisInput,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() {
                          _modoRegistro = false;
                          _errorGeneral = null;
                        }),
                        child: Container(
                          margin: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: !_modoRegistro
                                ? Colors.white
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(8),
                            boxShadow: !_modoRegistro
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.05),
                                      blurRadius: 4,
                                    ),
                                  ]
                                : [],
                          ),
                          child: Center(
                            child: Text(
                              "Iniciar sesión",
                              style: TextStyle(
                                fontWeight: !_modoRegistro
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                color: !_modoRegistro
                                    ? Colors.black
                                    : colorGrisTexto,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: GestureDetector(
                        onTap: () => setState(() {
                          _modoRegistro = true;
                          _errorGeneral = null;
                        }),
                        child: Container(
                          margin: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: _modoRegistro
                                ? Colors.white
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(8),
                            boxShadow: _modoRegistro
                                ? [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.05),
                                      blurRadius: 4,
                                    ),
                                  ]
                                : [],
                          ),
                          child: Center(
                            child: Text(
                              "Crear cuenta",
                              style: TextStyle(
                                fontWeight: _modoRegistro
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                                color: _modoRegistro
                                    ? Colors.black
                                    : colorGrisTexto,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Mensaje de error general
              if (_errorGeneral != null) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFDECEC),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    _errorGeneral!,
                    style: const TextStyle(color: colorRojoError, fontSize: 13),
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // --- CAMPOS PARA CREAR CUENTA ---
              if (_modoRegistro) ...[
                _InputFigma(
                  control: _nombreCompleto,
                  etiqueta: 'Nombre completo',
                  hint: 'Ej. Jorge Morales',
                  icono: Icons.person_outline,
                  validar: (v) {
                    if (v == null || v.trim().isEmpty) {
                      return 'Ingresa tu nombre completo';
                    }
                    if (v.trim().split(' ').length < 2) {
                      return 'Ingresa nombre y apellido';
                    }
                    return null;
                  },
                ),
                _InputFigma(
                  control: _email,
                  etiqueta: 'Correo electrónico',
                  hint: 'nombre@correo.com',
                  icono: Icons.mail_outline,
                  teclado: TextInputType.emailAddress,
                  validar: (v) {
                    if (v == null || v.isEmpty) return 'Ingresa tu correo';
                    if (!RegExp(
                      r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$',
                    ).hasMatch(v)) {
                      return 'Ingresa un correo válido (ej. nombre@correo.com)';
                    }
                    return null;
                  },
                ),
                _InputFigma(
                  control: _telefono,
                  etiqueta: 'Celular',
                  hint: 'Ej. 987654321',
                  icono: Icons.phone_outlined,
                  teclado: TextInputType.phone,
                  // 👇 AQUÍ SE APLICA EL LÍMITE Y EL FILTRO
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(9),
                  ],
                  validar: (v) {
                    if (v == null || v.isEmpty) return 'Ingresa tu celular';
                    if (v.length != 9) return 'El celular debe tener 9 dígitos';
                    return null;
                  },
                ),
                _InputFigma(
                  control: _clave,
                  etiqueta: 'Contraseña',
                  hint: '••••••••',
                  icono: Icons.lock_outline,
                  oculto: _ocultarClave,
                  sufijo: IconButton(
                    icon: Icon(
                      _ocultarClave
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                    onPressed: () =>
                        setState(() => _ocultarClave = !_ocultarClave),
                  ),
                  validar: (v) {
                    if (v == null || v.isEmpty) return 'Ingresa tu contraseña';
                    if (v.length < 8) return 'Mínimo 8 caracteres';
                    if (!v.contains(RegExp(r'[a-z]')))
                      return 'Falta una minúscula';
                    if (!v.contains(RegExp(r'[A-Z]')))
                      return 'Falta una mayúscula';
                    if (!v.contains(RegExp(r'[0-9]'))) return 'Falta un número';
                    if (!v.contains(RegExp(r'[@$!%*?&]'))) {
                      return 'Falta un símbolo (@, \$, !, %, *, ?, &)';
                    }
                    return null;
                  },
                ),

                _InputFigma(
                  control: _direccion,
                  etiqueta: 'Dirección de envío',
                  hint: 'Ej. Av. Lima 123, Miraflores',
                  icono: Icons.home_outlined,
                  validar: (v) => (v == null || v.trim().isEmpty)
                      ? 'Ingresa tu dirección'
                      : null,
                ),

                // Checkbox de términos
                Row(
                  children: [
                    Checkbox(
                      value: _aceptaTerminos,
                      onChanged: (v) {
                        setState(() => _aceptaTerminos = v ?? false);
                      },
                      activeColor: colorVerde,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                    const Expanded(
                      child: Text.rich(
                        TextSpan(
                          text: 'Acepto los ',
                          style: TextStyle(fontSize: 13, color: colorGrisTexto),
                          children: [
                            TextSpan(
                              text: 'Términos',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.black87,
                              ),
                            ),
                            TextSpan(text: ' y la '),
                            TextSpan(
                              text: 'Política de privacidad',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.black87,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
              ],

              // --- CAMPOS PARA INICIAR SESIÓN ---
              if (!_modoRegistro) ...[
                _InputFigma(
                  control: _email,
                  etiqueta: 'Correo electrónico',
                  hint: 'nombre@correo.com',
                  icono: Icons.mail_outline,
                  teclado: TextInputType.emailAddress,
                  validar: (v) {
                    if (v == null || v.isEmpty) return 'Ingresa tu correo';
                    if (!RegExp(
                      r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$',
                    ).hasMatch(v)) {
                      return 'Ingresa un correo válido (ej. nombre@correo.com)';
                    }
                    return null;
                  },
                ),
                _InputFigma(
                  control: _clave,
                  etiqueta: 'Contraseña',
                  hint: '••••••••',
                  icono: Icons.lock_outline,
                  oculto: _ocultarClave,
                  sufijo: IconButton(
                    icon: Icon(
                      _ocultarClave
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                    onPressed: () =>
                        setState(() => _ocultarClave = !_ocultarClave),
                  ),
                  validar: (v) =>
                      (v == null || v.isEmpty) ? 'Ingresa tu contraseña' : null,
                ),
                // Recordar y Olvidé contraseña
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Checkbox(
                          value: _recordarme,
                          onChanged: (v) {
                            setState(() => _recordarme = v ?? false);
                          },
                          activeColor: colorVerde,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                        const Text(
                          "Recordarme",
                          style: TextStyle(fontSize: 13, color: colorGrisTexto),
                        ),
                      ],
                    ),
                    TextButton(
                      onPressed: () {},
                      child: const Text(
                        "¿Olvidaste tu clave?",
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.teal,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ],

              const SizedBox(height: 16),

              // Botón Principal
              SizedBox(
                height: 56,
                child: ElevatedButton(
                  onPressed: _enviando ? null : _enviar,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: colorVerde,
                    foregroundColor: Colors.black,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    elevation: 0,
                  ),
                  child: _enviando
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.black,
                          ),
                        )
                      : Text(
                          _modoRegistro
                              ? 'Crear mi cuenta'
                              : 'Ingresar a SmartZone',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                ),
              ),

              const SizedBox(height: 24),

              if (!_modoRegistro) ...[
                const Row(
                  children: [
                    Expanded(child: Divider(color: Colors.black12)),
                    Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        "o continúa con",
                        style: TextStyle(color: colorGrisTexto, fontSize: 12),
                      ),
                    ),
                    Expanded(child: Divider(color: Colors.black12)),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 56,
                        child: OutlinedButton(
                          onPressed: () {},
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Colors.black12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                            backgroundColor: Colors.white,
                          ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.language,
                                color: Colors.black87,
                                size: 20,
                              ),
                              SizedBox(width: 8),
                              Text(
                                "Google",
                                style: TextStyle(
                                  color: Colors.black87,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: SizedBox(
                        height: 56,
                        child: OutlinedButton(
                          onPressed: () {},
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Colors.black12),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                            backgroundColor: Colors.white,
                          ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                Icons.facebook,
                                color: Colors.blue,
                                size: 20,
                              ),
                              SizedBox(width: 8),
                              Text(
                                "Facebook",
                                style: TextStyle(
                                  color: Colors.black87,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],

              const SizedBox(height: 24),

              // Botón "Continuar como invitado"
              TextButton(
                onPressed: () {
                  Navigator.pushReplacement(
                    context,
                    MaterialPageRoute(builder: (context) => const Arranque()),
                  );
                },
                child: const Text(
                  "Continuar como invitado",
                  style: TextStyle(
                    color: colorGrisTexto,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    decoration: TextDecoration.underline,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InputFigma extends StatelessWidget {
  const _InputFigma({
    required this.control,
    required this.etiqueta,
    required this.icono,
    this.hint,
    this.validar,
    this.teclado,
    this.oculto = false,
    this.sufijo,
    this.inputFormatters,
  });

  final TextEditingController control;
  final String etiqueta;
  final String? hint;
  final IconData icono;
  final String? Function(String?)? validar;
  final TextInputType? teclado;
  final bool oculto;
  final Widget? sufijo;
  final List<TextInputFormatter>? inputFormatters;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
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
            obscureText: oculto,
            keyboardType: teclado,
            validator: validar,
            inputFormatters: inputFormatters, // <-- SE APLICA AQUÍ
            decoration: InputDecoration(
              hintText: hint,
              hintStyle: const TextStyle(color: Colors.black38, fontSize: 14),
              filled: true,
              fillColor: Colors.white,
              prefixIcon: Icon(icono, color: Colors.black45, size: 20),
              suffixIcon: sufijo,
              contentPadding: const EdgeInsets.symmetric(vertical: 16),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: Colors.black12),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: Colors.black12),
              ),
              errorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: colorRojoError, width: 1.5),
              ),
              focusedErrorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: colorRojoError, width: 2),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: Colors.black, width: 2),
              ),
              errorStyle: const TextStyle(color: colorRojoError, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}
