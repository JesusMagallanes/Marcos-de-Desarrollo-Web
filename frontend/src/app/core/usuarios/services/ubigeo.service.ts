import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of, shareReplay } from 'rxjs';
import { RUTAS_USUARIOS } from '../usuarios.routes';

/**
 * El ubigeo del Perú: departamentos, provincias y distritos.
 *
 * <p>Sirve para que el usuario ELIJA su distrito en vez de escribirlo. Escrito
 * a mano conviven «Miraflores», «miraflores» y «Mirafores» en la misma columna,
 * y con eso no se puede agrupar el reparto por zona ni saber cuánto se vende en
 * cada sitio.
 */
@Injectable({ providedIn: 'root' })
export class UbigeoService {
  private readonly http = inject(HttpClient);

  /*
   * Las listas no cambian nunca durante una sesión —las cambia una migración—
   * así que se guardan. Sin esto, abrir el modal tres veces son tres peticiones
   * idénticas, y el desplegable de provincias se repinta en cada tecla.
   */
  private departamentosCache?: Observable<string[]>;
  private provinciasCache = new Map<string, Observable<string[]>>();
  private distritosCache = new Map<string, Observable<string[]>>();

  departamentos(): Observable<string[]> {
    this.departamentosCache ??= this.http
      .get<string[]>(RUTAS_USUARIOS.ubigeo.departamentos)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.departamentosCache;
  }

  provincias(departamento: string): Observable<string[]> {
    if (!departamento) return of([]);

    let peticion = this.provinciasCache.get(departamento);
    if (!peticion) {
      peticion = this.http
        .get<string[]>(RUTAS_USUARIOS.ubigeo.provincias, {
          params: new HttpParams().set('departamento', departamento),
        })
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.provinciasCache.set(departamento, peticion);
    }
    return peticion;
  }

  distritos(departamento: string, provincia: string): Observable<string[]> {
    if (!departamento || !provincia) return of([]);

    const clave = `${departamento}|${provincia}`;
    let peticion = this.distritosCache.get(clave);
    if (!peticion) {
      peticion = this.http
        .get<string[]>(RUTAS_USUARIOS.ubigeo.distritos, {
          params: new HttpParams().set('departamento', departamento).set('provincia', provincia),
        })
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
      this.distritosCache.set(clave, peticion);
    }
    return peticion;
  }
}
