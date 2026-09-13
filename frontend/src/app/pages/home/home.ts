import {
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, Subscription, forkJoin, of, switchMap } from 'rxjs';
import {
  AuthService,
  BloqueCategoria,
  Carrusel,
  DescubrimientoService,
  Categoria,
  CategoriaService,
  ErrorApi,
  Producto,
  ProductoService,
  ValoracionDestacada,
  UbigeoService,
  ValoracionService,
} from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';
import { CarruselDescubrimiento } from '../../shared/descubrimiento';

interface Bloque {
  categoria: Categoria;
  productos: Producto[];
  chunks: Producto[][];
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, ProductoCard, CarruselDescubrimiento],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  private categoriaService = inject(CategoriaService);
  private productoService = inject(ProductoService);
  private valoracionService = inject(ValoracionService);
  private descubrimiento = inject(DescubrimientoService);
  private ubigeoService = inject(UbigeoService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  protected cargando = signal(true);

  /**
   * Los carruseles personalizados, en UNA sola petición.
   *
   * <p>El backend ya los devuelve resueltos y de-duplicados entre sí: pedirlos
   * por separado traería el mismo producto en «según tus intereses» y en
   * «tendencias», que es justo lo que su de-duplicación evita.
   *
   * <p>Empieza vacío y se rellena aparte de la portada. Si Descubrimiento no
   * contesta, la lista se queda vacía, no se pinta ninguna sección y el Home es
   * el de siempre. El usuario no tiene por qué enterarse de que existe un
   * recomendador, y menos de que se cayó.
   */
  protected carruseles = signal<Carrusel[]>([]);
  /** Para el esqueleto: distingue «cargando» de «no hay nada que enseñar». */
  protected descubriendo = signal(true);
  protected error = signal('');
  protected categorias = signal<Categoria[]>([]);

  /** La petición de descubrimiento en curso, para cortarla si cambia quién mira. */
  private peticionDescubrimiento: Subscription | null = null;
  private ultimoUsuarioId: number | null | undefined;

  constructor() {
    /*
     * Los carruseles se vuelven a pedir cada vez que cambia QUIEN mira.
     *
     * Se pedian una sola vez, al crear la pagina. Quien iniciaba sesion desde
     * la portada se quedaba viendo el Home del visitante anonimo —solo «lo mas
     * popular»— hasta que navegaba a otro sitio y volvia; y, peor, quien
     * cerraba sesion desde la portada seguia viendo SUS carruseles personales
     * en una pantalla que ya no era suya.
     *
     * Se compara el id y no la senal entera porque el usuario en memoria
     * tambien cambia al editar el perfil, y eso no es motivo para recargar.
     */
    effect(() => {
      const usuarioId = this.auth.usuario()?.id ?? null;
      if (usuarioId === this.ultimoUsuarioId) {
        return;
      }
      this.ultimoUsuarioId = usuarioId;
      untracked(() => this.cargarDescubrimiento());
    });
  }

  /*
   * Las tres listas llegan ya resueltas del servidor.
   *
   * Antes había una sola señal con el catálogo COMPLETO y de ella salían los
   * destacados, las ofertas y los bloques por categoría, filtrando y agrupando
   * aquí. Funcionaba, pero significaba descargar cada producto de la tienda
   * para enseñar unas decenas, y crecer con el catálogo aunque la pantalla
   * fuera siempre la misma.
   */
  protected destacados = signal<Producto[]>([]);
  protected enOferta = signal<Producto[]>([]);
  protected porCategoria = signal<BloqueCategoria[]>([]);
  /** Las 6 aprobadas mejor valoradas (más estrellas), para la sección de reseñas. */
  protected valoraciones = signal<ValoracionDestacada[]>([]);
  protected resenasChunks = computed(() => this.chunk(this.valoraciones(), 3));

  /**
   * Agrupación por categoría; sustituye a productosPorCategoria del
   * IndexController. La agrupación la hace el servidor; aquí solo se parte en
   * diapositivas para el carrusel, que es cosa de la vista.
   */
  protected bloques = computed<Bloque[]>(() =>
    this.porCategoria().map((b) => ({
      categoria: b.categoria,
      productos: b.productos,
      chunks: this.chunk(b.productos, 6),
    })),
  );

  /** "Productos Top": los diez que manda el servidor, en slides de 5. */
  protected topChunks = computed(() => this.chunk(this.destacados(), 5));

  /** Carrusel de ofertas, en slides de 6. */
  protected ofertaChunks = computed(() => this.chunk(this.enOferta(), 6));

  ngOnInit(): void {
    /*
     * El descubrimiento NO va dentro de este forkJoin, a proposito.
     *
     * Con forkJoin, un fallo de Descubrimiento tumbaria tambien la portada y el
     * Home entero se quedaria en el mensaje de error. Separado, cada mitad de
     * la pantalla llega cuando puede y el fallo de una no arrastra a la otra.
     * Lo pide el efecto del constructor, que ademas lo repite al cambiar de
     * sesion.
     */
    forkJoin({
      categorias: this.categoriaService.listar(),
      portada: this.productoService.portada(),
      valoraciones: this.valoracionService.destacadas(),
    }).subscribe({
      next: ({ categorias, portada, valoraciones }) => {
        this.categorias.set(categorias);
        this.destacados.set(portada.destacados);
        this.enOferta.set(portada.ofertas);
        this.porCategoria.set(portada.porCategoria);
        this.valoraciones.set(valoraciones);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  /** Imagen del producto reseñado, con fallback a la imagen por defecto. */
  imagenProducto(valoracion: ValoracionDestacada): string {
    return valoracion.productoImagenUrl || '/Img/img.png';
  }

  /**
   * Pide los carruseles para quien esta mirando ahora.
   *
   * <p>Primero la zona, despues el Home. Si el usuario ya guardo su direccion
   * en el perfil se traduce a ubigeo y se manda con la peticion; es un dato
   * que el mismo dio, para envios, y reutilizarlo no pide permisos nuevos ni
   * geolocalizacion. Nunca se mandan coordenadas, solo el codigo del INEI.
   * Antes las dos peticiones salian a la vez y el Home llegaba siempre sin
   * zona: las tendencias caian a nacional en la primera carga.
   *
   * <p>Sin direccion, o si el ubigeo falla, se pide igual: se pierde precision,
   * no la seccion.
   */
  private cargarDescubrimiento(): void {
    // Si cambia la sesion con una peticion en vuelo, la respuesta vieja no
    // debe pisar a la nueva.
    this.peticionDescubrimiento?.unsubscribe();
    this.descubriendo.set(true);

    this.peticionDescubrimiento = this.zona()
      .pipe(
        switchMap((codigo) => {
          this.descubrimiento.fijarUbigeo(codigo);
          return this.descubrimiento.home(12);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        this.carruseles.set(res?.carruseles ?? []);
        this.descubriendo.set(false);
      });
  }

  /** El ubigeo de la direccion guardada, o `null`; nunca falla. */
  private zona(): Observable<string | null> {
    const dir = this.auth.usuario()?.direccion;
    if (!dir?.departamento || !dir.provincia || !dir.distrito) {
      return of(null);
    }
    return this.ubigeoService.codigo(dir.departamento, dir.provincia, dir.distrito);
  }

  private chunk<T>(elementos: T[], tamaño: number): T[][] {
    const trozos: T[][] = [];
    for (let i = 0; i < elementos.length; i += tamaño) {
      trozos.push(elementos.slice(i, i + tamaño));
    }
    return trozos;
  }
}
