import 'package:flutter/material.dart';
import '../login/pagina_login.dart';

class BienvenidaScreen extends StatefulWidget {
  final VoidCallback alTerminar; // Para "Comenzar"
  final VoidCallback alOmitir; // Para "Omitir"

  const BienvenidaScreen({
    super.key,
    required this.alTerminar,
    required this.alOmitir, // <-- Añadido
  });

  @override
  State<BienvenidaScreen> createState() => _BienvenidaScreenState();
}

class _BienvenidaScreenState extends State<BienvenidaScreen> {
  final PageController _pageController = PageController();
  int _paginaActual = 0;

  static const Color colorVerde = Color(0xFF00FF7F);
  static const Color colorFondo = Color(0xFFF8F9FA);

  final List<Map<String, dynamic>> _paginas = [
    {
      'etiquetaIzq': 'Para ti',
      'iconoIzq': Icons.person_outline,
      'iconoCentro': Icons.auto_awesome,
      'etiquetaDer': 'Asesor IA',
      'iconoDer': Icons.smart_toy_outlined,
      'titulo': 'Encuentra el equipo ideal sin ser experto',
      'descripcion':
          'SmartZone te recomienda productos según tus búsquedas y un asesor con IA responde tus dudas técnicas antes de comprar.',
    },
    {
      'etiquetaIzq': 'Ofertas',
      'iconoIzq': Icons.local_offer_outlined,
      'iconoCentro': Icons.shopping_bag_outlined,
      'etiquetaDer': 'Envíos',
      'iconoDer': Icons.local_shipping_outlined,
      'titulo': 'Los mejores precios del mercado',
      'descripcion':
          'Descubre ofertas exclusivas y recibe tus productos en la puerta de tu casa con envíos rápidos y seguros.',
    },
    {
      'etiquetaIzq': 'Seguridad',
      'iconoIzq': Icons.security_outlined,
      'iconoCentro': Icons.payment,
      'etiquetaDer': 'Rápido',
      'iconoDer': Icons.flash_on_outlined,
      'titulo': 'Compra con total confianza',
      'descripcion':
          'Pagos protegidos, garantía en todos los productos y un proceso de compra en menos de 2 minutos.',
    },
  ];

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: colorFondo,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 1. Botón "Omitir" (Va directo a la tienda)
              Align(
                alignment: Alignment.topRight,
                child: TextButton(
                  onPressed: widget.alOmitir, // <-- Usa alOmitir
                  child: const Text(
                    "Omitir",
                    style: TextStyle(color: Colors.black54, fontSize: 16),
                  ),
                ),
              ),
              const SizedBox(height: 10),

              // 2. PageView
              Expanded(
                flex: 5,
                child: PageView.builder(
                  controller: _pageController,
                  onPageChanged: (index) {
                    setState(() => _paginaActual = index);
                  },
                  itemCount: _paginas.length,
                  itemBuilder: (context, index) {
                    final pagina = _paginas[index];
                    return _buildContenidoPagina(pagina);
                  },
                ),
              ),

              // 3. Indicadores
              Row(
                mainAxisAlignment: MainAxisAlignment.start,
                children: List.generate(_paginas.length, (index) {
                  final esActual = _paginaActual == index;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    margin: const EdgeInsets.only(right: 8),
                    width: esActual ? 24 : 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: esActual ? colorVerde : Colors.grey.shade400,
                      borderRadius: BorderRadius.circular(3),
                    ),
                  );
                }),
              ),

              const Spacer(),

              // 4. Botón principal (Comenzar / Siguiente)
              SizedBox(
                width: double.infinity,
                height: 56,
                child: ElevatedButton(
                  onPressed: () {
                    if (_paginaActual == _paginas.length - 1) {
                      widget.alTerminar(); // <-- Va al Login
                    } else {
                      _pageController.nextPage(
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeInOut,
                      );
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: colorVerde,
                    foregroundColor: Colors.black,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16.0),
                    ),
                    elevation: 0,
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        _paginaActual == _paginas.length - 1
                            ? "Comenzar"
                            : "Siguiente",
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.arrow_forward, size: 20),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 16),

              // 5. "¿Ya tienes cuenta? Inicia sesión"
              Center(
                child: GestureDetector(
                  onTap: () {
                    Navigator.pushReplacement(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const PaginaLogin(),
                      ),
                    );
                  },
                  child: RichText(
                    text: const TextSpan(
                      text: '¿Ya tienes cuenta? ',
                      style: TextStyle(color: Colors.black54, fontSize: 14),
                      children: [
                        TextSpan(
                          text: 'Inicia sesión',
                          style: TextStyle(
                            color: Color(0xFF008F4C),
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContenidoPagina(Map<String, dynamic> pagina) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(24.0),
          decoration: BoxDecoration(
            color: const Color(0xFF1E1E1E),
            borderRadius: BorderRadius.circular(24.0),
          ),
          child: Column(
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(pagina['iconoIzq'], size: 16),
                      const SizedBox(width: 4),
                      Text(
                        pagina['etiquetaIzq'],
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Container(
                width: 160,
                height: 160,
                decoration: const BoxDecoration(
                  color: colorVerde,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  pagina['iconoCentro'],
                  size: 80,
                  color: Colors.black,
                ),
              ),
              const SizedBox(height: 20),
              Align(
                alignment: Alignment.centerRight,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(pagina['iconoDer'], size: 16),
                      const SizedBox(width: 4),
                      Text(
                        pagina['etiquetaDer'],
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 32),
        Text(
          pagina['titulo'],
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.bold,
            color: Colors.black87,
            height: 1.2,
          ),
        ),
        const SizedBox(height: 16),
        Text(
          pagina['descripcion'],
          style: const TextStyle(
            fontSize: 16,
            color: Colors.black54,
            height: 1.4,
          ),
        ),
      ],
    );
  }
}
