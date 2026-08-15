import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Coordenadas, Ubicacion } from '../../shared/ubicacion/ubicacion';
import {
  AuthService,
  ColaboradorService,
  ErrorApi,
  ETIQUETA_ADJUNTO,
  REGLAS_POR_PERSONA,
  SolicitudResponse,
  TAMANO_MAXIMO_BYTES,
  TIPOS_ACEPTADOS,
  TipoAdjunto,
  TipoDocumento,
  TipoPersona,
} from '../../core';

/** La versión que se le enseña al usuario. El backend rechaza cualquier otra. */
const TERMINOS_VERSION = '2026-08';

/**
 * Recuerda que ya vio la guía.
 *
 * <p>Enseñarla de nuevo a quien vuelve --porque le faltaba una foto, porque
 * cerró la pestaña-- es tratarle como si no supiera lo que viene a hacer.
 */
const CLAVE_GUIA_VISTA = 'sz_vender_guia_vista';

/**
 * Solicitar ser colaborador.
 *
 * <p>Tres bloques: quién eres, dónde estás y qué vendes; más los documentos que
 * lo demuestran. Los archivos se suben según se eligen, no al enviar: así un
 * error de tecleo en cualquier campo no obliga a repetir varios megas de fotos.
 */
@Component({
  selector: 'app-vender',
  imports: [RouterLink, DatePipe, Ubicacion],
  templateUrl: './vender.html',
  styleUrl: './vender.css',
})
export class Vender implements OnInit {
  private colaboradores = inject(ColaboradorService);
  private auth = inject(AuthService);

  protected readonly tiposAceptados = TIPOS_ACEPTADOS;
  protected readonly etiquetas = ETIQUETA_ADJUNTO;

  /*
   * En qué punto está: primero se elige si vende como persona o como empresa
   * --que es de lo que depende todo lo demás-- y luego el formulario.
   *
   * Se separa porque el trámite pide documentos que hay que ir a buscar. Verlo
   * de golpe, con los cuatro bloques y las subidas, hace que se abandone; saber
   * antes qué te van a pedir hace que lo prepares.
   */
  protected paso = signal<'eleccion' | 'formulario'>('eleccion');

  /** Qué modal de guía está abierto; `null` = ninguno. */
  protected guiaAbierta = signal<TipoPersona | null>(null);

  protected cargando = signal(true);
  protected enviando = signal(false);
  protected error = signal('');
  protected solicitud = signal<SolicitudResponse | null>(null);

  /* ── Formulario ── */

  protected tipoPersona = signal<TipoPersona>('NATURAL');
  protected tipoDocumento = signal<TipoDocumento>('DNI');
  protected documento = signal('');
  protected nombreTitular = signal('');
  protected representanteLegal = signal('');
  protected fechaNacimiento = signal('');

  protected nombreComercial = signal('');
  protected telefonoContacto = signal('');
  protected rubro = signal('');
  protected descripcion = signal('');

  protected direccion = signal('');
  protected referencia = signal('');
  protected distrito = signal('');
  protected provincia = signal('');
  protected departamento = signal('');
  protected codigoPostal = signal('');

  /*
   * El punto en el mapa. Opcional: hay quien vende desde su casa y no quiere
   * marcarla. Cuando se marca, quien revisa puede comprobar de un vistazo que
   * el negocio queda donde dice la dirección escrita.
   */
  protected ubicacion = signal<Coordenadas | null>(null);

  protected aceptaTerminos = signal(false);

  /** Archivos ya subidos, por tipo. El valor es el nombre, para enseñarlo. */
  protected subidos = signal<Partial<Record<TipoAdjunto, string>>>({});
  protected subiendo = signal<TipoAdjunto | null>(null);

  /** De qué depende todo lo demás. Sale del modelo, no de ifs sueltos. */
  protected reglas = computed(() => REGLAS_POR_PERSONA[this.tipoPersona()]);

  protected adjuntosQueFaltan = computed(() =>
    this.reglas().adjuntos.filter((t) => !this.subidos()[t]),
  );

  protected puedeEnviar = computed(
    () =>
      !this.enviando() &&
      this.aceptaTerminos() &&
      this.adjuntosQueFaltan().length === 0 &&
      this.documento().trim().length >= 8 &&
      this.nombreTitular().trim().length >= 3 &&
      this.nombreComercial().trim().length >= 3 &&
      this.descripcion().trim().length >= 30 &&
      this.direccion().trim().length > 0 &&
      /^[0-9]{5}$/.test(this.codigoPostal().trim()) &&
      /^[0-9]{9}$/.test(this.telefonoContacto().trim()),
  );

  /** Qué pasos del formulario ya se completaron, para el indicador superior. */
  protected pasosCompletos = computed(() => ({
    1: this.documento().trim().length >= 8 && this.nombreTitular().trim().length >= 3,
    2: this.adjuntosQueFaltan().length === 0,
    3:
      this.nombreComercial().trim().length >= 3 &&
      this.descripcion().trim().length >= 30 &&
      /^[0-9]{9}$/.test(this.telefonoContacto().trim()),
    4: this.direccion().trim().length > 0 && /^[0-9]{5}$/.test(this.codigoPostal().trim()),
  }));

  /** Ya es colaborador: no tiene nada que solicitar. */
  protected yaEsColaborador = computed(() => this.auth.usuario()?.rol === 'COLABORADOR');

  ngOnInit(): void {
    // Quien ya la vio va directo al formulario.
    if (localStorage.getItem(CLAVE_GUIA_VISTA)) {
      this.paso.set('formulario');
    }

    this.colaboradores.miSolicitud().subscribe({
      next: (s) => {
        this.solicitud.set(s);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  /** Cambiar de persona a empresa invalida el documento y los campos del otro tipo. */
  protected cambiarTipoPersona(tipo: TipoPersona): void {
    this.tipoPersona.set(tipo);
    this.tipoDocumento.set(REGLAS_POR_PERSONA[tipo].documentos[0]);
    this.documento.set('');
    if (tipo === 'NATURAL') {
      this.representanteLegal.set('');
    } else {
      this.fechaNacimiento.set('');
    }
  }

  protected elegirArchivo(tipo: TipoAdjunto, evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (!archivo) return;

    // Se comprueba antes de subir para no gastar cinco megas de datos del
    // usuario en una petición que el backend va a rechazar igual.
    if (archivo.size > TAMANO_MAXIMO_BYTES) {
      this.error.set(`"${archivo.name}" pesa más de 5 MB. Prueba con una foto más pequeña.`);
      entrada.value = '';
      return;
    }

    this.subiendo.set(tipo);
    this.error.set('');

    this.colaboradores.subirAdjunto(tipo, archivo).subscribe({
      next: (r) => {
        // Subir otra vez el mismo tipo SUSTITUYE en el backend, así que aquí
        // basta con quedarse con el último.
        this.subidos.update((s) => ({ ...s, [tipo]: r.nombreOriginal }));
        this.subiendo.set(null);
      },
      error: (e: ErrorApi) => {
        this.subiendo.set(null);
        this.error.set(e.mensaje);
        entrada.value = '';
      },
    });
  }

  protected enviar(): void {
    if (!this.puedeEnviar()) return;

    this.enviando.set(true);
    this.error.set('');

    this.colaboradores
      .solicitar({
        tipoPersona: this.tipoPersona(),
        tipoDocumento: this.tipoDocumento(),
        documento: this.documento().trim().toUpperCase(),
        nombreTitular: this.nombreTitular().trim(),
        representanteLegal: this.reglas().exigeRepresentante
          ? this.representanteLegal().trim()
          : undefined,
        fechaNacimiento: this.reglas().exigeFechaNacimiento
          ? this.fechaNacimiento()
          : undefined,
        nombreComercial: this.nombreComercial().trim(),
        telefonoContacto: this.telefonoContacto().trim(),
        rubro: this.rubro().trim(),
        descripcion: this.descripcion().trim(),
        domicilio: {
          direccion: this.direccion().trim(),
          referencia: this.referencia().trim() || undefined,
          distrito: this.distrito().trim(),
          provincia: this.provincia().trim(),
          departamento: this.departamento().trim(),
          codigoPostal: this.codigoPostal().trim(),
          latitud: this.ubicacion()?.latitud,
          longitud: this.ubicacion()?.longitud,
        },
        aceptaTerminos: true,
        terminosVersion: TERMINOS_VERSION,
      })
      .subscribe({
        next: (s) => {
          this.solicitud.set(s);
          this.enviando.set(false);
        },
        error: (e: ErrorApi) => {
          this.enviando.set(false);
          this.error.set(e.mensaje);
        },
      });
  }

  /**
   * Tras un rechazo se puede volver a intentar: se limpia la solicitud de la
   * pantalla para que aparezca el formulario otra vez.
   */
  protected volverAIntentar(): void {
    this.solicitud.set(null);
    this.subidos.set({});
    this.error.set('');
  }

  /**
   * El rol viaja DENTRO del token, así que aprobar no basta: hasta que se
   * renueve, el backend le sigue viendo como cliente. Se renueva aquí para que
   * el menú de vendedor aparezca sin tener que cerrar sesión.
   */
  protected activarMiCuenta(): void {
    this.auth.refrescar().subscribe({
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  /* ── Guía previa ── */

  /**
   * Abre la explicación del tipo elegido. No cambia todavía el formulario: el
   * usuario está mirando qué implica, no decidiendo.
   */
  protected abrirGuia(tipo: TipoPersona): void {
    this.guiaAbierta.set(tipo);
  }

  protected cerrarGuia(): void {
    this.guiaAbierta.set(null);
  }

  /**
   * Escape cierra el modal.
   *
   * <p>Es lo que espera cualquiera que use el teclado, y sin esto la unica
   * salida seria el raton: la guia se convertiria en una trampa para quien
   * navega con tabulador.
   */
  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    if (this.guiaAbierta()) {
      this.cerrarGuia();
    }
  }

  /** Los documentos que le van a pedir, según lo que haya elegido. */
  protected adjuntosDeLaGuia(tipo: TipoPersona): readonly TipoAdjunto[] {
    return REGLAS_POR_PERSONA[tipo].adjuntos;
  }

  /** Sale de la guía con el tipo ya elegido: no se le vuelve a preguntar. */
  protected empezar(tipo: TipoPersona): void {
    this.cambiarTipoPersona(tipo);
    this.guiaAbierta.set(null);
    this.paso.set('formulario');
    localStorage.setItem(CLAVE_GUIA_VISTA, '1');
  }

  /**
   * Saltarse la guía.
   *
   * <p>Quien ya sabe lo que hace no tiene por qué leer tres tarjetas. Se guarda
   * igual que si la hubiera visto, para no volver a interponerla.
   */
  protected omitirGuia(): void {
    this.guiaAbierta.set(null);
    this.paso.set('formulario');
    localStorage.setItem(CLAVE_GUIA_VISTA, '1');
  }

  /** Volver a la elección desde el formulario, por si se equivocó de tipo. */
  protected volverALaGuia(): void {
    this.paso.set('eleccion');
  }
}
