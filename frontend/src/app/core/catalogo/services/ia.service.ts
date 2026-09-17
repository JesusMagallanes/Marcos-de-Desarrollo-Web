import { HttpClient } from '@angular/common/http';
import { ApplicationRef, Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, defer, map, of, switchMap } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  AnalisisModeracion,
  AyudaResena,
  BusquedaInterpretada,
  BusquedaPorFoto,
  Comparacion,
  ComoCorregir,
  Complementos,
  RedaccionRequest,
  RedaccionSugerida,
  RespuestaFicha,
  ResumenResenas,
  RevisionFoto,
} from '../models';

/**
 * IA aplicada — servicio `catalogo` (:8081).
 *
 * Todas las lecturas devuelven `null` cuando la IA no aporta nada: el backend
 * responde 204 (sin modelo configurado, texto sospechoso, proveedor caído) y
 * eso es un estado normal. Las funciones públicas —interpretar la búsqueda y
 * resumir reseñas— además tragan sus errores: son mejoras, y la pantalla tiene
 * que funcionar igual sin ellas.
 *
 * Las que se lanzan solas al abrir una pantalla (resumir reseñas, interpretar
 * la búsqueda, complementos del carrito) salen en segundo plano: ver
 * {@link IaService#enSegundoPlano}.
 */
@Injectable({ providedIn: 'root' })
export class IaService {
  private readonly http = inject(HttpClient);
  private readonly app = inject(ApplicationRef);

  private readonly disponibleSig = signal(false);
  /** Si hay modelo configurado. Mientras no se sepa, `false`: no se enseñan botones que no van a funcionar. */
  readonly disponible = this.disponibleSig.asReadonly();

  constructor() {
    this.http
      .get<{ disponible: boolean }>(RUTAS_CATALOGO.ia.estado)
      .pipe(catchError(() => of({ disponible: false })))
      .subscribe((e) => this.disponibleSig.set(!!e?.disponible));
  }

  /**
   * Una mejora que nadie ha pedido con un clic no debe retener la pantalla.
   *
   * Cada petición de `HttpClient` cuenta como tarea pendiente de la aplicación,
   * y la pantalla de carga de `main.ts` espera a que no quede ninguna. Una
   * llamada al modelo tarda segundos: lanzada junto a las de la página, dejaba
   * «Cargando SmartZone» encima de una tienda que ya estaba lista. Aquí la
   * petición sale cuando la aplicación queda estable; en una navegación
   * posterior eso es inmediato o casi, así que el usuario no nota diferencia.
   */
  private enSegundoPlano<T>(peticion: () => Observable<T>): Observable<T> {
    return defer(() => this.app.whenStable()).pipe(switchMap(peticion));
  }

  /** POST /ia/busqueda/interpretar — público. */
  interpretar(consulta: string): Observable<BusquedaInterpretada | null> {
    return this.enSegundoPlano(() =>
      this.http.post<BusquedaInterpretada | null>(RUTAS_CATALOGO.ia.interpretar, {
        consulta: consulta.slice(0, 240),
      }),
    ).pipe(catchError(() => of(null)));
  }

  /** GET /ia/productos/{id}/resumen-resenas — público. */
  resumenResenas(productoId: number): Observable<ResumenResenas | null> {
    return this.enSegundoPlano(() =>
      this.http.get<ResumenResenas | null>(RUTAS_CATALOGO.ia.resumenResenas(productoId)),
    ).pipe(catchError(() => of(null)));
  }

  /** POST /ia/productos/redactar — tienda o colaborador. Los errores sí se propagan: hay alguien esperando. */
  redactar(dto: RedaccionRequest): Observable<RedaccionSugerida | null> {
    return this.http.post<RedaccionSugerida | null>(RUTAS_CATALOGO.ia.redactar, dto).pipe(map((r) => r ?? null));
  }

  /** GET /ia/moderacion/productos/{id} — responde siempre, con reglas si no hay modelo. */
  analizarProducto(id: number): Observable<AnalisisModeracion> {
    return this.http.get<AnalisisModeracion>(RUTAS_CATALOGO.ia.moderacionProducto(id));
  }

  /** GET /ia/moderacion/valoraciones/{id}. */
  analizarValoracion(id: number): Observable<AnalisisModeracion> {
    return this.http.get<AnalisisModeracion>(RUTAS_CATALOGO.ia.moderacionValoracion(id));
  }

  /* ── Cliente ── */

  /** POST /ia/productos/{id}/preguntar — contestada con la ficha. Errores propagados: hay alguien esperando. */
  preguntar(productoId: number, pregunta: string): Observable<RespuestaFicha | null> {
    return this.http.post<RespuestaFicha | null>(RUTAS_CATALOGO.ia.preguntar(productoId), {
      pregunta: pregunta.trim().slice(0, 300),
    });
  }

  /** POST /ia/comparar — siempre trae la tabla; la explicación solo con IA. */
  comparar(productoIds: number[], uso?: string): Observable<Comparacion> {
    return this.http.post<Comparacion>(RUTAS_CATALOGO.ia.comparar, {
      productoIds,
      uso: uso?.trim().slice(0, 200) || null,
    });
  }

  /** POST /ia/carrito/complementos — mejora silenciosa: sin IA o con error, null. */
  complementos(productoIds: number[]): Observable<Complementos | null> {
    return this.enSegundoPlano(() =>
      this.http.post<Complementos | null>(RUTAS_CATALOGO.ia.complementos, {
        productoIds: productoIds.slice(0, 20),
      }),
    ).pipe(catchError(() => of(null)));
  }

  /** POST /ia/resenas/ayuda — preguntas para escribir mejor; nunca el texto. */
  ayudaResena(productoId: number, calificacion: number, borrador: string): Observable<AyudaResena | null> {
    return this.http
      .post<AyudaResena | null>(RUTAS_CATALOGO.ia.ayudaResena, {
        productoId,
        calificacion,
        borrador: borrador.slice(0, 1500),
      })
      .pipe(catchError(() => of(null)));
  }

  /** POST /ia/busqueda/foto — la foto no se guarda. */
  buscarPorFoto(archivo: File): Observable<BusquedaPorFoto | null> {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    return this.http.post<BusquedaPorFoto | null>(RUTAS_CATALOGO.ia.busquedaFoto, datos);
  }

  /* ── Vendedor ── */

  /** POST /ia/productos/revisar-foto — solo fotos ya subidas a la tienda. */
  revisarFoto(url: string, nombre: string): Observable<RevisionFoto | null> {
    return this.http
      .post<RevisionFoto | null>(RUTAS_CATALOGO.ia.revisarFoto, { url, nombre })
      .pipe(catchError(() => of(null)));
  }

  /** GET /ia/productos/mios/{id}/como-corregir — solo productos propios rechazados. */
  comoCorregir(productoId: number): Observable<ComoCorregir | null> {
    return this.http.get<ComoCorregir | null>(RUTAS_CATALOGO.ia.comoCorregir(productoId));
  }
}
