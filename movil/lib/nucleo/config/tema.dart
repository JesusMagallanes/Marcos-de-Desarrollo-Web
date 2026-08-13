import 'package:flutter/material.dart';

/// La paleta de la tienda, la misma que usa la web.
///
/// Los valores salen de `frontend/src/styles/Style.css` y del pie: verde de
/// marca para las acciones, gris oscuro para el pie y las cabeceras, y los
/// mismos grises de texto. Así la app no parece otra tienda.
class Paleta {
  const Paleta._();

  /// Verde principal de la marca (botones, enlaces, acentos).
  static const verde = Color(0xFF06B56C);

  /// Verde vivo del logotipo y de los estados de éxito.
  static const verdeVivo = Color(0xFF00DE80);

  /// Fondo de los iconos y bloques suaves.
  static const verdeSuave = Color(0xFFEAFAF2);

  /// Gris del pie de la web.
  static const grisOscuro = Color(0xFF2D2D2D);

  static const texto = Color(0xFF202020);
  static const textoSuave = Color(0xFF6B7671);
  static const textoTenue = Color(0xFF8A9B90);

  static const borde = Color(0xFFE6E9E7);
  static const fondo = Color(0xFFF3F4F6);
  static const rojo = Color(0xFFDC3545);
}

ThemeData construirTema() {
  final base = ThemeData.light(useMaterial3: true);

  return base.copyWith(
    colorScheme: ColorScheme.fromSeed(
      seedColor: Paleta.verde,
      primary: Paleta.verde,
      surface: Colors.white,
      error: Paleta.rojo,
    ),
    scaffoldBackgroundColor: Paleta.fondo,

    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Paleta.texto,
      elevation: 0,
      scrolledUnderElevation: 1,
      centerTitle: false,
      titleTextStyle: TextStyle(
        color: Paleta.texto,
        fontSize: 18,
        fontWeight: FontWeight.w700,
      ),
    ),

    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: const BorderSide(color: Paleta.borde),
      ),
      margin: EdgeInsets.zero,
    ),

    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: Paleta.verde,
        foregroundColor: Colors.white,
        elevation: 0,
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
      ),
    ),

    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: Paleta.verde,
        side: const BorderSide(color: Paleta.verde),
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),

    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: Paleta.verde),
    ),

    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Paleta.borde),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Paleta.borde),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: Paleta.verde, width: 1.5),
      ),
      labelStyle: const TextStyle(color: Paleta.textoSuave),
    ),

    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      indicatorColor: Paleta.verdeSuave,
      elevation: 3,
      labelTextStyle: WidgetStatePropertyAll(
        const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
      ),
    ),

    chipTheme: base.chipTheme.copyWith(
      backgroundColor: Paleta.verdeSuave,
      labelStyle: const TextStyle(color: Paleta.verde, fontWeight: FontWeight.w600),
      side: BorderSide.none,
    ),

    dividerTheme: const DividerThemeData(color: Paleta.borde, thickness: 1),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: Paleta.verde),
  );
}
