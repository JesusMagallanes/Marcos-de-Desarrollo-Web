import { Component, HostListener, computed, effect, input, output, signal } from '@angular/core';
import {
  DireccionEntrega,
  direccionCompleta,
  direccionLugarCompleto,
  direccionVacia,
} from '../../core';
import { Coordenadas, Ubicacion } from '../ubicacion/ubicacion';

/**
 * Dónde entregar el pedido, preguntado en condiciones.
 *
 * <h4>Por qué un modal y no los campos sueltos en el carrito</h4>
 *
 * <p>Son nueve datos. Puestos en la columna del resumen empujaban el botón de
 * pagar fuera de la pantalla, y el comprador rellenaba a ciegas algo que ni
 * siquiera había decidido mirar. Aquí se piden una vez, con sitio para el mapa,
 * y en el carrito queda una línea que se lee de un vistazo.
 *
 * <p>El mapa no es decoración: una dirección escrita no dice si el sitio es el
 * correcto, y el comprador la escribe una vez y ya no la vuelve a leer. Viéndola
 * en el mapa la comprueba antes de pagar, que es cuando todavía se puede
 * arreglar.
 */
@Component({
  selector: 'app-direccion-modal',
  imports: [Ubicacion],
  templateUrl: './direccion-modal.html',
  styleUrl: './direccion-modal.css',
})
export class DireccionModal {
  /** La dirección que ya había, para editarla en vez de empezar de cero. */
  readonly inicial = input<DireccionEntrega | null>(null);

  /** Texto del botón que confirma. Cambia según desde dónde se abra. */
  readonly textoAceptar = input('Usar esta dirección');

  /**
   * Si se pregunta por quién recibe y su teléfono.
   *
   * <p>En el perfil no: ahí se guarda un SITIO, y el nombre y el teléfono ya
   * están en la cuenta. Al pagar sí, porque un regalo va a nombre de otro y ese
   * dato cambia en cada pedido, no al mudarse.
   */
  readonly pedirReceptor = input(true);

  readonly guardar = output<DireccionEntrega>();
  readonly cerrar = output<void>();

  protected receptorNombre = signal('');
  protected telefonoContacto = signal('');
  protected calle = signal('');
  protected numero = signal('');
  protected referencia = signal('');
  protected distrito = signal('');
  protected provincia = signal('');
  protected departamento = signal('');
  protected codigoPostal = signal('');
  protected ubicacion = signal<Coordenadas | null>(null);

  /**
   * Solo se enseñan los errores cuando el comprador ha intentado guardar.
   * Pintarlos en rojo mientras todavía está escribiendo el primer campo es
   * regañarle por no haber terminado.
   */
  protected intentado = signal(false);

  /*
   * Se rellena con lo que ya había. Es un `effect` y no una lectura en el
   * constructor porque el modal puede abrirse antes de que la dirección llegue
   * del perfil.
   */
  private volcado = effect(() => {
    const d = this.inicial();
    if (!d) return;
    this.receptorNombre.set(d.receptorNombre);
    this.telefonoContacto.set(d.telefonoContacto);
    this.calle.set(d.calle);
    this.numero.set(d.numero);
    this.referencia.set(d.referencia ?? '');
    this.distrito.set(d.distrito);
    this.provincia.set(d.provincia);
    this.departamento.set(d.departamento);
    this.codigoPostal.set(d.codigoPostal);
    this.ubicacion.set(
      d.latitud != null && d.longitud != null
        ? { latitud: d.latitud, longitud: d.longitud }
        : null,
    );
  });

  protected actual = computed<DireccionEntrega>(() => ({
    ...direccionVacia(),
    receptorNombre: this.receptorNombre(),
    telefonoContacto: this.telefonoContacto(),
    calle: this.calle(),
    numero: this.numero(),
    referencia: this.referencia(),
    distrito: this.distrito(),
    provincia: this.provincia(),
    departamento: this.departamento(),
    codigoPostal: this.codigoPostal(),
    latitud: this.ubicacion()?.latitud,
    longitud: this.ubicacion()?.longitud,
  }));

  protected completa = computed(() =>
    this.pedirReceptor()
      ? direccionCompleta(this.actual())
      : direccionLugarCompleto(this.actual()),
  );

  /* ── Errores, uno por campo y solo tras intentar guardar ── */

  protected errorNombre = computed(() =>
    this.pedirReceptor() && this.intentado() && this.receptorNombre().trim().length < 3
      ? 'Escribe el nombre de quien recibe'
      : '',
  );
  protected errorTelefono = computed(() =>
    this.pedirReceptor() && this.intentado() && !/^[0-9]{9}$/.test(this.telefonoContacto().trim())
      ? 'Son 9 dígitos, sin espacios'
      : '',
  );
  protected errorCalle = computed(() =>
    this.intentado() && !this.calle().trim() ? 'Falta la calle o avenida' : '',
  );
  protected errorNumero = computed(() =>
    this.intentado() && !this.numero().trim() ? 'Falta el número' : '',
  );
  protected errorCodigoPostal = computed(() =>
    this.intentado() && !/^[0-9]{5}$/.test(this.codigoPostal().trim())
      ? 'Son 5 dígitos'
      : '',
  );
  protected errorDistrito = computed(() =>
    this.intentado() && !this.distrito().trim() ? 'Falta el distrito' : '',
  );
  protected errorProvincia = computed(() =>
    this.intentado() && !this.provincia().trim() ? 'Falta la provincia' : '',
  );
  protected errorDepartamento = computed(() =>
    this.intentado() && !this.departamento().trim() ? 'Falta el departamento' : '',
  );

  protected aceptar(): void {
    this.intentado.set(true);
    if (!this.completa()) return;
    this.guardar.emit(this.actual());
  }

  /** Escape cierra: es lo que todo el mundo intenta primero. */
  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    this.cerrar.emit();
  }
}
