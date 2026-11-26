import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../nucleo/api/error_api.dart';
import '../../nucleo/config/tema.dart';
import '../../nucleo/modelos/direccion.dart';
import '../../nucleo/servicios/compras_servicio.dart';
import '../../nucleo/servicios/sesion.dart';
import '../../widgets/boton_accion.dart';
import '../../widgets/cabecera_seccion.dart';
import '../../widgets/campo_etiquetado.dart';

/// Edición de la dirección única del perfil.
///
/// El backend mantiene una sola dirección por usuario, así que aquí no hay
/// lista ni selección múltiple. Cuando se abre desde el checkout se devuelve la
/// dirección guardada para que el paso de entrega siga con los datos vigentes.
class PaginaDirecciones extends StatefulWidget {
  const PaginaDirecciones({super.key, this.desdeCheckout = false});

  final bool desdeCheckout;

  @override
  State<PaginaDirecciones> createState() => _PaginaDireccionesEstado();
}

class _PaginaDireccionesEstado extends State<PaginaDirecciones> {
  final _formulario = GlobalKey<FormState>();
  late final TextEditingController _calle;
  late final TextEditingController _numero;
  late final TextEditingController _referencia;
  late final TextEditingController _codigoPostal;
  late final TextEditingController _distrito;
  late final TextEditingController _provincia;
  late final TextEditingController _departamento;
  late final TextEditingController _pais;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    final direccion = context.read<Sesion>().usuario?.direccionDetallada;
    _calle = TextEditingController(text: direccion?.calle ?? '');
    _numero = TextEditingController(text: direccion?.numero ?? '');
    _referencia = TextEditingController(text: direccion?.referencia ?? '');
    _codigoPostal = TextEditingController(text: direccion?.codigoPostal ?? '');
    _distrito = TextEditingController(text: direccion?.distrito ?? '');
    _provincia = TextEditingController(text: direccion?.provincia ?? '');
    _departamento = TextEditingController(text: direccion?.departamento ?? '');
    _pais = TextEditingController(text: direccion?.pais ?? 'PE');
  }

  @override
  void dispose() {
    for (final controlador in <TextEditingController>[
      _calle,
      _numero,
      _referencia,
      _codigoPostal,
      _distrito,
      _provincia,
      _departamento,
      _pais,
    ]) {
      controlador.dispose();
    }
    super.dispose();
  }

  Direccion _direccion() => Direccion(
        calle: _calle.text,
        numero: _numero.text,
        referencia: _referencia.text,
        codigoPostal: _codigoPostal.text,
        distrito: _distrito.text,
        provincia: _provincia.text,
        departamento: _departamento.text,
        pais: _pais.text,
      );

  Future<void> _guardar() async {
    if (!(_formulario.currentState?.validate() ?? false)) return;
    final usuario = context.read<Sesion>().usuario;
    if (usuario == null) {
      _mostrarError('Inicia sesión para guardar la dirección.');
      return;
    }

    setState(() => _guardando = true);
    final direccion = _direccion();
    final compras = context.read<ComprasServicio>();
    final sesion = context.read<Sesion>();
    try {
      await compras.guardarDireccion(usuario.id, direccion);
      final error = await sesion.recargar();
      if (!mounted) return;
      if (error != null) {
        _mostrarError(error.mensaje);
        return;
      }
      Navigator.of(context).pop(widget.desdeCheckout ? direccion : true);
    } on ErrorApi catch (e) {
      if (mounted) _mostrarError(e.mensaje);
    } catch (_) {
      if (mounted) _mostrarError('No pudimos guardar la dirección.');
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  void _mostrarError(String mensaje) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(mensaje),
          backgroundColor: Paleta.rojo,
          behavior: SnackBarBehavior.floating,
        ),
      );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8F8F8),
        body: Column(
          children: [
            const CabeceraSeccion(
              titulo: 'Dirección de entrega',
              subtitulo: 'Una sola dirección en tu perfil',
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 16),
                children: [
                  Form(
                    key: _formulario,
                    child: Column(
                      children: [
                        CampoEtiquetado(
                          etiqueta: 'Calle y nombre de vía',
                          controlador: _calle,
                          icono: Icons.place_outlined,
                          sugerencia: 'Ej.: Av. Larco',
                          validar: (v) =>
                              (v ?? '').trim().isEmpty ? 'Indica la calle' : null,
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              flex: 2,
                              child: CampoEtiquetado(
                                etiqueta: 'Número',
                                controlador: _numero,
                                teclado: TextInputType.number,
                                validar: (v) => (v ?? '').trim().isEmpty
                                    ? 'Indica el número'
                                    : null,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              flex: 3,
                              child: CampoEtiquetado(
                                etiqueta: 'Código postal',
                                controlador: _codigoPostal,
                                teclado: TextInputType.number,
                                validar: (v) => RegExp(r'^[0-9]{5}$')
                                        .hasMatch((v ?? '').trim())
                                    ? null
                                    : 'Son 5 dígitos',
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Departamento',
                                controlador: _departamento,
                                validar: (v) => (v ?? '').trim().isEmpty
                                    ? 'Indica el departamento'
                                    : null,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Provincia',
                                controlador: _provincia,
                                validar: (v) => (v ?? '').trim().isEmpty
                                    ? 'Indica la provincia'
                                    : null,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'Distrito',
                                controlador: _distrito,
                                validar: (v) => (v ?? '').trim().isEmpty
                                    ? 'Indica el distrito'
                                    : null,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: CampoEtiquetado(
                                etiqueta: 'País',
                                controlador: _pais,
                                validar: (v) => (v ?? '').trim().length >= 2
                                    ? null
                                    : 'Indica el país',
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        CampoEtiquetado(
                          etiqueta: 'Referencia (opcional)',
                          controlador: _referencia,
                          sugerencia: 'Ej.: frente al parque',
                          maxLineas: 2,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Paleta.bordeTarjeta),
                    ),
                    child: const Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Icons.info_outline,
                            size: 18, color: Paleta.textoSecundario),
                        SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'El backend guarda una sola dirección por cuenta. '
                            'El mapa, las coordenadas y el tiempo estimado de '
                            'entrega todavía no están disponibles en la API.',
                            style: TextStyle(
                              fontSize: 11,
                              height: 1.4,
                              color: Paleta.textoSecundario,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            Container(
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: Paleta.bordeTarjeta)),
              ),
              padding: EdgeInsets.only(
                left: 16,
                right: 16,
                top: 12,
                bottom: MediaQuery.paddingOf(context).bottom + 12,
              ),
              child: BotonAccion(
                etiqueta: _guardando
                    ? 'Guardando…'
                    : widget.desdeCheckout
                        ? 'Usar esta dirección'
                        : 'Guardar dirección',
                icono: Icons.save_outlined,
                habilitado: !_guardando,
                alPulsar: _guardar,
              ),
            ),
          ],
        ),
      );
}
