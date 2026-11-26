import 'package:flutter/material.dart';

/// Método de pago REAL devuelto por `/api/metodos-pago`.
///
/// No hay nombres de marcas inventados: lo que se pinta sale del catálogo del
/// backend. `requierePasarela` se deduce del `tipo`, igual que hace la web.
class MetodoPago {
  final int id;
  final String nombre;
  final String descripcion;
  final String tipo;

  const MetodoPago({
    required this.id,
    required this.nombre,
    required this.descripcion,
    required this.tipo,
  });

  bool get requierePasarela => tipo == 'MERCADOPAGO';
  bool get esContraEntrega => tipo == 'EFECTIVO';

  IconData get icono {
    switch (tipo) {
      case 'MERCADOPAGO':
        return Icons.account_balance_wallet_outlined;
      case 'EFECTIVO':
        return Icons.money_outlined;
      default:
        return Icons.payment_outlined;
    }
  }

  factory MetodoPago.desdeJson(Map<String, dynamic> j) => MetodoPago(
        id: (j['id'] as num).toInt(),
        nombre: (j['name'] ?? '') as String,
        descripcion: (j['description'] ?? '') as String,
        tipo: (j['tipo'] ?? 'OTRO') as String,
      );
}
