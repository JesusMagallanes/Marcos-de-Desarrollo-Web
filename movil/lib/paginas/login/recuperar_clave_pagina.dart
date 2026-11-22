// lib/paginas/login/recuperar_clave_pagina.dart
import 'package:flutter/material.dart';

class RecuperarClavePagina extends StatefulWidget {
  const RecuperarClavePagina({super.key});

  @override
  State<RecuperarClavePagina> createState() => _RecuperarClavePaginaState();
}

class _RecuperarClavePaginaState extends State<RecuperarClavePagina> {
  final _emailController = TextEditingController();
  bool _enviado = false;
  bool _enviando = false;

  static const Color colorVerde = Color(0xFF00FF7F);
  static const Color colorFondo = Color(0xFFF8F9FA);
  static const Color colorGrisTexto = Color(0xFF6C757D);

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  void _enviarEnlace() async {
    if (_emailController.text.trim().isEmpty) return;

    setState(() => _enviando = true);

    // Aquí iría la llamada real a tu backend para enviar el correo.
    // Ejemplo: await context.read<Sesion>().recuperarClave(_emailController.text);

    // Simulamos un pequeño delay para que se vea el estado de carga
    await Future.delayed(const Duration(seconds: 1));

    if (!mounted) return;

    setState(() {
      _enviando = false;
      _enviado = true;
    });
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

              // Ícono de llave
              Container(
                width: 100,
                height: 100,
                decoration: const BoxDecoration(
                  color: Color(0xFFE0F7E9), // Verde muy claro
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.key, size: 50, color: Colors.black87),
              ),
              const SizedBox(height: 24),

              // Títulos
              const Text(
                "¿Olvidaste tu contraseña?",
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: Colors.black87,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                "Ingresa tu correo y te enviaremos un enlace para crear una nueva.",
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 14,
                  color: colorGrisTexto,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 32),

              // Formulario / Inputs
              if (!_enviado) ...[
                // Etiqueta del correo
                const Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    "Correo electrónico",
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                      color: Colors.black87,
                    ),
                  ),
                ),
                const SizedBox(height: 8),

                // Campo de texto
                TextFormField(
                  controller: _emailController,
                  keyboardType: TextInputType.emailAddress,
                  decoration: InputDecoration(
                    hintText: "jorge.morales@gmail.com",
                    hintStyle: const TextStyle(
                      color: Colors.black38,
                      fontSize: 14,
                    ),
                    filled: true,
                    fillColor: Colors.white,
                    prefixIcon: const Icon(
                      Icons.mail_outline,
                      color: Colors.black45,
                      size: 20,
                    ),
                    contentPadding: const EdgeInsets.symmetric(vertical: 16),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Colors.black12),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(color: Colors.black12),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: const BorderSide(
                        color: Colors.black,
                        width: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // Botón Enviar
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: ElevatedButton(
                    onPressed: _enviando ? null : _enviarEnlace,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: colorVerde,
                      foregroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
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
                        : const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.send, size: 18),
                              SizedBox(width: 8),
                              Text(
                                "Enviar enlace",
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 16,
                                ),
                              ),
                            ],
                          ),
                  ),
                ),
              ],

              // Estado: Enlace Enviado
              if (_enviado) ...[
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFFE0F7E9), // Verde claro de fondo
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: const Color(0xFF00FF7F).withOpacity(0.3),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.check_circle_outline,
                        color: Color(0xFF008F4C),
                        size: 24,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: RichText(
                          text: const TextSpan(
                            style: TextStyle(
                              fontSize: 13,
                              color: Colors.black87,
                              height: 1.4,
                            ),
                            children: [
                              TextSpan(
                                text: 'Enlace enviado. ',
                                style: TextStyle(fontWeight: FontWeight.bold),
                              ),
                              TextSpan(
                                text: 'Revisa tu bandeja de entrada y spam.',
                                style: TextStyle(fontWeight: FontWeight.normal),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 32),

              // Volver a Iniciar Sesión
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
                        color: Color(0xFF008F4C),
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
}
