import { Component, HostListener, computed, effect, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import {
  DireccionEntrega,
  UbigeoService,
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
  private ubigeo = inject(UbigeoService);

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

  /*
   * Perú se elige de una lista; fuera de Perú se escribe a mano.
   *
   * El catálogo del INEI solo cubre Perú, y es donde vende la tienda: ahí el
   * distrito tiene que salir de la lista o no hay forma de agrupar el reparto
   * ni de que la paquetería reconozca el nombre. Para el resto del mundo no hay
   * catálogo que ofrecer, así que se acepta lo que escriba el usuario.
   */
  protected enPeru = signal(true);
  protected paisOtro = signal('');

  protected departamentos = toSignal(this.ubigeo.departamentos(), { initialValue: [] as string[] });

  protected provincias = toSignal(
    toObservable(this.departamento).pipe(switchMap((dep) => this.ubigeo.provincias(dep))),
    { initialValue: [] as string[] },
  );

  /** Las dos juntas: los distritos dependen del par, no de un campo suelto. */
  private zona = computed<[string, string]>(() => [this.departamento(), this.provincia()]);

  protected distritos = toSignal(
    toObservable(this.zona).pipe(switchMap(([dep, pro]) => this.ubigeo.distritos(dep, pro))),
    { initialValue: [] as string[] },
  );

  /**
   * Al cambiar de departamento se vacían provincia y distrito.
   *
   * <p>Sin esto queda «Cusco» con la provincia «Lima» todavía seleccionada: una
   * combinación que no existe, que el backend rechaza y que el usuario no
   * entiende porque en pantalla se ve un desplegable relleno.
   */
  protected elegirDepartamento(valor: string): void {
    this.departamento.set(valor);
    this.provincia.set('');
    this.distrito.set('');
  }

  protected elegirProvincia(valor: string): void {
    this.provincia.set(valor);
    this.distrito.set('');
  }

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
    const pais = (d.pais ?? 'PE').toUpperCase();
    this.enPeru.set(pais === 'PE');
    this.paisOtro.set(pais === 'PE' ? '' : pais);
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
    pais: this.enPeru() ? 'PE' : this.paisOtro().trim().toUpperCase(),
    latitud: this.ubicacion()?.latitud,
    longitud: this.ubicacion()?.longitud,
  }));

  protected completa = computed(() => {
    const lugar = this.pedirReceptor()
      ? direccionCompleta(this.actual())
      : direccionLugarCompleto(this.actual());
    // Fuera de Perú el país deja de ser un supuesto y hay que decirlo.
    return lugar && (this.enPeru() || /^[A-Za-z]{2}$/.test(this.paisOtro().trim()));
  });

  protected errorPais = computed(() =>
    !this.enPeru() && this.intentado() && !/^[A-Za-z]{2}$/.test(this.paisOtro().trim())
      ? 'Son dos letras, como CL o EC'
      : '',
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
