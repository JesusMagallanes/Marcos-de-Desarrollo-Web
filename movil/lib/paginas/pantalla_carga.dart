import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../nucleo/config/tema.dart';

/// La misma pantalla de carga que la web, pieza por pieza.
///
/// En `frontend/src/index.html` son cinco animaciones CSS y aquí son cinco
/// controladores, con los mismos tiempos y los mismos colores:
///
///   anillo    1.8s  el aro que se expande y se desvanece
///   latido    1.6s  el icono que respira
///   aparecer  0.9s  la marca, con 0.3s de retraso
///   deslizar  1.2s  la barra de progreso que cruza
///   brincar   1.2s  los tres puntos, desfasados 0.2s
///
/// Los SVG son los mismos ficheros que sirve la web, copiados a assets: si
/// mañana cambia el logotipo, cambia en los dos sitios.
class PantallaCarga extends StatefulWidget {
  const PantallaCarga({super.key, this.mostrarAvisoLento = false});

  /// El aviso de conexión lenta, que en la web aparece a los 3,5 s.
  final bool mostrarAvisoLento;

  @override
  State<PantallaCarga> createState() => _PantallaCargaEstado();
}

class _PantallaCargaEstado extends State<PantallaCarga> with TickerProviderStateMixin {
  late final AnimationController _anillo;
  late final AnimationController _latido;
  late final AnimationController _aparecer;
  late final AnimationController _deslizar;
  late final AnimationController _puntos;

  @override
  void initState() {
    super.initState();

    _anillo = AnimationController(vsync: this, duration: const Duration(milliseconds: 1800))
      ..repeat();
    _latido = AnimationController(vsync: this, duration: const Duration(milliseconds: 1600))
      ..repeat(reverse: true);
    _deslizar = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))
      ..repeat();
    _puntos = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))
      ..repeat();

    // La marca entra una sola vez, con el mismo retraso que en la web.
    _aparecer = AnimationController(vsync: this, duration: const Duration(milliseconds: 900));
    Future.delayed(const Duration(milliseconds: 300), () {
      if (mounted) _aparecer.forward();
    });
  }

  @override
  void dispose() {
    for (final c in [_anillo, _latido, _aparecer, _deslizar, _puntos]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // El mismo degradado del index.html: blanco a un verde muy claro.
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFFFFFFF), Color(0xFFF3FFF8)],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _iconoConAnillo(),
                const SizedBox(height: 26),
                _marca(),
                const SizedBox(height: 26),
                _barra(),
                const SizedBox(height: 26),
                _texto(),
                // Se reserva el hueco siempre para que el bloque no dé un
                // salto cuando aparece el aviso.
                SizedBox(
                  height: 56,
                  child: AnimatedOpacity(
                    opacity: widget.mostrarAvisoLento ? 1 : 0,
                    duration: const Duration(milliseconds: 600),
                    child: const Padding(
                      padding: EdgeInsets.only(top: 14, left: 40, right: 40),
                      child: Text(
                        'Tu conexión parece lenta. Seguimos cargando la tienda…',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 12, color: Color(0xFFA8B5AC), height: 1.4),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// `anillo` + `latido`: el aro que se expande detrás del icono que respira.
  Widget _iconoConAnillo() {
    return SizedBox(
      width: 160,
      height: 160,
      child: Stack(
        alignment: Alignment.center,
        children: [
          AnimatedBuilder(
            animation: _anillo,
            builder: (_, _) {
              final t = _anillo.value;
              // Mismos valores que el CSS: escala 0.9 -> 1.55, opacidad 1 -> 0.
              return Transform.scale(
                scale: 0.9 + (1.55 - 0.9) * t,
                child: Opacity(
                  opacity: (1 - t).clamp(0.0, 1.0),
                  child: Container(
                    width: 150,
                    height: 150,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(color: const Color(0xB300DE80), width: 2),
                    ),
                  ),
                ),
              );
            },
          ),
          AnimatedBuilder(
            animation: _latido,
            builder: (_, hijo) {
              final t = Curves.easeInOut.transform(_latido.value);
              return Transform.scale(
                scale: 1 + 0.07 * t,
                child: Opacity(opacity: 1 - 0.15 * t, child: hijo),
              );
            },
            child: SvgPicture.asset('assets/carga/icon-sz.svg', width: 104),
          ),
        ],
      ),
    );
  }

  /// `aparecer`: "Smart" + "Zone" entrando juntos.
  Widget _marca() {
    return FadeTransition(
      opacity: CurvedAnimation(parent: _aparecer, curve: Curves.easeOut),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SvgPicture.asset('assets/carga/Smart.svg', height: 40),
          const SizedBox(width: 6),
          SvgPicture.asset('assets/carga/Zone.svg', height: 40),
        ],
      ),
    );
  }

  /// `deslizar`: la barra recorre el carril de izquierda a derecha.
  Widget _barra() {
    return Container(
      width: 230,
      height: 6,
      decoration: BoxDecoration(
        color: const Color(0xFFE5E5E5),
        borderRadius: BorderRadius.circular(999),
      ),
      clipBehavior: Clip.antiAlias,
      child: AnimatedBuilder(
        animation: _deslizar,
        builder: (_, _) {
          final t = Curves.easeInOut.transform(_deslizar.value);
          // El CSS mueve el 40% de ancho de -100% a 350% de su propio tamaño.
          const anchoRelativo = 0.4;
          final desplazamiento = (-1 + 4.5 * t) * (230 * anchoRelativo);
          return Stack(
            children: [
              Positioned(
                left: desplazamiento,
                child: Container(
                  width: 230 * anchoRelativo,
                  height: 6,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(999),
                    gradient: const LinearGradient(
                      colors: [Color(0xFF00DE80), Color(0xFF06B56C)],
                    ),
                    boxShadow: const [
                      BoxShadow(color: Color(0xCC00DE80), blurRadius: 12),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  /// `brincar`: el texto y los tres puntos que saltan desfasados.
  Widget _texto() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Text(
          'Cargando SmartZone',
          style: TextStyle(
            fontSize: 13,
            letterSpacing: 0.5,
            color: Color(0xFF8A9B90),
          ),
        ),
        const SizedBox(width: 4),
        ...List.generate(3, (i) => _punto(i)),
      ],
    );
  }

  Widget _punto(int indice) {
    return AnimatedBuilder(
      animation: _puntos,
      builder: (_, _) {
        // Cada punto va 0,2 s por detrás del anterior, como el
        // `animation-delay` del CSS.
        final desfase = indice * 0.1667;
        final t = (_puntos.value + desfase) % 1.0;
        // El CSS sube en el 50% del ciclo y baja después.
        final salto = t < 0.5 ? t * 2 : (1 - t) * 2;
        final suave = Curves.easeInOut.transform(salto);

        return Padding(
          padding: const EdgeInsets.only(left: 3),
          child: Transform.translate(
            offset: Offset(0, -4 * suave),
            child: Opacity(
              opacity: 0.4 + 0.6 * suave,
              child: Container(
                width: 4,
                height: 4,
                decoration: const BoxDecoration(
                  color: Paleta.verde,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
