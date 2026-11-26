import 'package:flutter/material.dart';

import '../../nucleo/modelos/carrito.dart';
import '../../nucleo/modelos/direccion.dart';
import '../../nucleo/modelos/metodo_pago.dart';
import '../../nucleo/modelos/usuario.dart';

/// Estado del checkout mientras el usuario recorre los tres pasos.
///
/// Vive en manos de [PaginaCheckout], así que vuelve atrás sin perder nada y no
/// se escribe en almacenamiento local: ningún dato de pago se persiste en el
/// dispositivo.
class CheckoutDraft {
  CheckoutDraft({required this.carrito, required this.usuario})
      : direccionGuardada = usuario.direccionDetallada {
    nombre.text = usuario.nombreCompleto;
    correo.text = usuario.email;
    telefono.text = usuario.telefono;

    final direccion = usuario.direccionDetallada;
    if (direccion != null) {
      calle.text = direccion.calle;
      numero.text = direccion.numero;
      referencia.text = direccion.referencia;
      codigoPostal.text = direccion.codigoPostal;
      distrito.text = direccion.distrito;
      provincia.text = direccion.provincia;
      departamento.text = direccion.departamento;
      pais.text = direccion.pais;
    }
  }

  final Carrito carrito;
  final Usuario usuario;
  final Direccion? direccionGuardada;

  final nombre = TextEditingController();
  final correo = TextEditingController();
  final telefono = TextEditingController();

  final calle = TextEditingController();
  final numero = TextEditingController();
  final referencia = TextEditingController();
  final codigoPostal = TextEditingController();
  final distrito = TextEditingController();
  final provincia = TextEditingController();
  final departamento = TextEditingController();
  final pais = TextEditingController(text: 'PE');

  String modalidad = 'envio';
  MetodoPago? metodoSeleccionado;
  int paso = 1;

  Direccion direccionActual() => Direccion(
        calle: calle.text,
        numero: numero.text,
        referencia: referencia.text,
        codigoPostal: codigoPostal.text,
        distrito: distrito.text,
        provincia: provincia.text,
        departamento: departamento.text,
        pais: pais.text.isEmpty ? 'PE' : pais.text,
      );

  void aplicarDireccion(Direccion direccion) {
    calle.text = direccion.calle;
    numero.text = direccion.numero;
    referencia.text = direccion.referencia;
    codigoPostal.text = direccion.codigoPostal;
    distrito.text = direccion.distrito;
    provincia.text = direccion.provincia;
    departamento.text = direccion.departamento;
    pais.text = direccion.pais;
  }

  void dispose() {
    for (final controlador in <TextEditingController>[
      nombre,
      correo,
      telefono,
      calle,
      numero,
      referencia,
      codigoPostal,
      distrito,
      provincia,
      departamento,
      pais,
    ]) {
      controlador.dispose();
    }
  }
}
