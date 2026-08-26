import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
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
   * Estas listas no cambian durante una sesión: las cambia una migración. Sin
   * caché, abrir el modal de dirección tres veces son tres peticiones idénticas,
   * y el desplegable de provincias se repide cada vez que se toca el de arriba.
   *
   * La caché la pone ahora `cacheInterceptor`, con un plazo de 24 horas. Aquí
   * había tres cachés escritas a mano —un campo y dos `Map`— que hacían lo
   * mismo pero obligaban a este servicio a llevar la cuenta de qué combinación
   * de departamento y provincia ya se había preguntado. Como el interceptor
   * guarda por URL completa, esa contabilidad sale sola.
   */

  departamentos(): Observable<string[]> {
    return this.http.get<string[]>(RUTAS_USUARIOS.ubigeo.departamentos);
  }

  provincias(departamento: string): Observable<string[]> {
    // Sin departamento no hay nada que preguntar: `of([])` ahorra una petición
    // que el backend contestaría con una lista vacía.
    if (!departamento) return of([]);

    return this.http.get<string[]>(RUTAS_USUARIOS.ubigeo.provincias, {
      params: new HttpParams().set('departamento', departamento),
    });
  }

  distritos(departamento: string, provincia: string): Observable<string[]> {
    if (!departamento || !provincia) return of([]);

    return this.http.get<string[]>(RUTAS_USUARIOS.ubigeo.distritos, {
      params: new HttpParams().set('departamento', departamento).set('provincia', provincia),
    });
  }
}
