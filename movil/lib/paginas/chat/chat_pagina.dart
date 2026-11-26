// lib/paginas/chat/chat_pagina.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../nucleo/modelos/mensaje_chat.dart';
import '../../nucleo/servicios/chat_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/agregar_al_carrito.dart';
import '../producto/pagina_producto.dart';

class ChatPagina extends StatefulWidget {
  const ChatPagina({super.key});

  @override
  State<ChatPagina> createState() => _ChatPaginaState();
}

class _ChatPaginaState extends State<ChatPagina> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final List<MensajeChat> _mensajes = [];
  final ChatServicio _chatServicio = ChatServicio();
  bool _escribiendo = false;

  @override
  void initState() {
    super.initState();

    final sesion = context.read<Sesion>();
    final String nombreUsuario = sesion.usuario?.nombre ?? "";

    String saludo = nombreUsuario.isNotEmpty
        ? "¡Hola $nombreUsuario! Soy tu asesor. ¿Qué equipo estás buscando?"
        : "¡Hola! Soy tu asesor. ¿Qué equipo estás buscando?";

    _mensajes.add(MensajeChat(texto: saludo, esUsuario: false));
  }

  Future<void> _enviarMensaje() async {
    final texto = _textController.text.trim();
    if (texto.isEmpty) return;

    setState(() {
      _mensajes.add(MensajeChat(texto: texto, esUsuario: true));
      _textController.clear();
      _escribiendo = true;
    });
    _scrollAbajo();

    final respuestaBot = await _chatServicio.enviarMensaje(texto);

    setState(() {
      _mensajes.add(respuestaBot);
      _escribiendo = false;
    });
    _scrollAbajo();
  }

  void _scrollAbajo() {
    Future.delayed(const Duration(milliseconds: 100), () {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: _buildAppBar(),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Text(
              "Hoy, ahora",
              style: TextStyle(color: Colors.grey, fontSize: 12),
            ),
          ),
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: _mensajes.length + (_escribiendo ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == _mensajes.length && _escribiendo) {
                  return _buildIndicadorEscribiendo();
                }
                return _buildMensaje(_mensajes[index]);
              },
            ),
          ),
          _buildSugerencias(),
          _buildInput(),
        ],
      ),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      backgroundColor: Colors.white,
      elevation: 0.5,
      leading: IconButton(
        icon: const Icon(Icons.arrow_back_ios, color: Colors.black, size: 20),
        onPressed: () => Navigator.pop(context),
      ),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: const BoxDecoration(
              color: Color(0xFF00E676),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.bolt, color: Colors.black, size: 20),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                "Asesor SmartZone",
                style: TextStyle(
                  color: Colors.black,
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
              Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: const BoxDecoration(
                      color: Colors.green,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Text(
                    "En línea • Asistente con IA",
                    style: TextStyle(color: Colors.grey, fontSize: 12),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
      // Se eliminó la sección actions (los tres puntitos)
    );
  }

  Widget _buildMensaje(MensajeChat mensaje) {
    final esUsuario = mensaje.esUsuario;
    return Align(
      alignment: esUsuario ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        child: Column(
          crossAxisAlignment: esUsuario
              ? CrossAxisAlignment.end
              : CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: esUsuario ? const Color(0xFF1E1E1E) : Colors.white,
                borderRadius: BorderRadius.circular(16).copyWith(
                  bottomRight: esUsuario
                      ? const Radius.circular(4)
                      : const Radius.circular(16),
                  bottomLeft: !esUsuario
                      ? const Radius.circular(4)
                      : const Radius.circular(16),
                ),
              ),
              child: Text(
                mensaje.texto,
                style: TextStyle(
                  color: esUsuario ? Colors.white : Colors.black87,
                  fontSize: 14,
                ),
              ),
            ),
            if (mensaje.producto != null)
              _buildTarjetaProducto(mensaje.producto!),
          ],
        ),
      ),
    );
  }

  Widget _buildTarjetaProducto(ProductoRecomendado producto) {
    return Container(
      margin: const EdgeInsets.only(top: 8),
      width: 280,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: const Color(0xFFF3F4F6),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: producto.imagenUrl != null
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(8),
                          child: CachedNetworkImage(
                            imageUrl: producto.imagenUrl!,
                            fit: BoxFit.cover,
                            placeholder: (context, url) => const Center(
                              child: SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              ),
                            ),
                            errorWidget: (context, url, error) =>
                                const Icon(Icons.laptop, color: Colors.grey),
                          ),
                        )
                      : const Icon(Icons.laptop, color: Colors.grey),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        producto.categoria.toUpperCase(),
                        style: const TextStyle(
                          fontSize: 9,
                          color: Colors.grey,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        producto.nombre,
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          const Icon(
                            Icons.star,
                            color: Colors.orange,
                            size: 12,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            producto.rating > 0
                                ? "${producto.rating} (${producto.reviews})"
                                : "Nuevo",
                            style: const TextStyle(
                              fontSize: 10,
                              color: Colors.grey,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Text(
                            "S/ ${producto.precio.toStringAsFixed(2)}",
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          if (producto.precioAnterior != null) ...[
                            const SizedBox(width: 6),
                            Text(
                              "S/ ${producto.precioAnterior!.toStringAsFixed(2)}",
                              style: const TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                                decoration: TextDecoration.lineThrough,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Row(
            children: [
              Expanded(
                child: TextButton(
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => PaginaProducto(productoId: producto.id),
                      ),
                    );
                  },
                  child: const Text(
                    "Ver detalle",
                    style: TextStyle(color: Colors.black),
                  ),
                ),
              ),
              Expanded(
                child: ElevatedButton(
                  // El asesor puede recomendar algo agotado: el botón lo dice
                  // en vez de prometer y fallar al pulsarlo.
                  onPressed: producto.sinStock
                      ? null
                      : () => agregarAlCarrito(
                          context,
                          productoId: producto.id,
                          nombre: producto.nombre,
                        ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF00E676),
                    foregroundColor: Colors.black,
                    shape: const RoundedRectangleBorder(
                      borderRadius: BorderRadius.only(
                        bottomRight: Radius.circular(16),
                      ),
                    ),
                  ),
                  child: Text(
                    producto.sinStock ? 'Sin stock' : 'Agregar',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildIndicadorEscribiendo() {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _PuntoAnimado(delay: 0),
            _PuntoAnimado(delay: 150),
            _PuntoAnimado(delay: 300),
          ],
        ),
      ),
    );
  }

  Widget _buildSugerencias() {
    final sugerencias = [
      "Busco una laptop para estudiar",
      "¿Qué laptop me recomiendas por 2000 soles?",
      "Quiero comparar laptops",
      "¿Tienen garantía los productos?",
    ];

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: sugerencias.map((texto) => _buildChip(texto)).toList(),
      ),
    );
  }

  Widget _buildChip(String texto) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: ActionChip(
        label: Text(texto, style: const TextStyle(fontSize: 12)),
        onPressed: () {
          _textController.text = texto;
          _enviarMensaje();
        },
        backgroundColor: Colors.white,
        side: BorderSide(color: Colors.grey.shade300),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
    );
  }

  Widget _buildInput() {
    return Container(
      padding: const EdgeInsets.all(16),
      color: Colors.white,
      child: Row(
        children: [
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: const Color(0xFFF5F5F5),
                borderRadius: BorderRadius.circular(24),
              ),
              child: Row(
                children: [
                  const Padding(
                    padding: EdgeInsets.only(left: 16, right: 8),
                    child: Icon(
                      Icons.chat_bubble_outline,
                      color: Colors.grey,
                      size: 20,
                    ),
                  ),
                  Expanded(
                    child: TextField(
                      controller: _textController,
                      decoration: const InputDecoration(
                        hintText: "Escribe tu consulta...",
                        border: InputBorder.none,
                        hintStyle: TextStyle(color: Colors.grey),
                      ),
                      onSubmitted: (_) => _enviarMensaje(),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),
          Container(
            decoration: const BoxDecoration(
              color: Color(0xFF00E676),
              shape: BoxShape.circle,
            ),
            child: IconButton(
              icon: const Icon(Icons.send, color: Colors.black),
              onPressed: _enviarMensaje,
            ),
          ),
        ],
      ),
    );
  }
}

class _PuntoAnimado extends StatefulWidget {
  final int delay;
  const _PuntoAnimado({required this.delay});
  @override
  State<_PuntoAnimado> createState() => _PuntoAnimadoState();
}

class _PuntoAnimadoState extends State<_PuntoAnimado>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _animation = Tween<double>(
      begin: 0,
      end: 1,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));
    Future.delayed(Duration(milliseconds: widget.delay), () {
      if (mounted) _controller.repeat(reverse: true);
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _animation,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 2),
        width: 6,
        height: 6,
        decoration: const BoxDecoration(
          color: Colors.grey,
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}
