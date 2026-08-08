import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { ENVIO } from '../../shared/config/constantes';
import { AgregarItemRequest, CambiarCantidadRequest, Carrito, CarritoItem } from '../models';
// Dependencia cruzada legítima: el carrito solo se pide si hay sesión.
import { AuthService } from '../../usuarios/services/auth.service';

/** Carrito — servicio `compras` (:8083). */
@Injectable({ providedIn: 'root' })
export class CarritoService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private readonly itemsSig = signal<CarritoItem[]>([]);
  private readonly subtotalSig = signal(0);

  readonly items = this.itemsSig.asReadonly();
  readonly subtotal = this.subtotalSig.asReadonly();

  readonly cantidadTotal = computed(() =>
    this.itemsSig().reduce((acc, i) => acc + i.cantidad, 0),
  );
  readonly vacio = computed(() => this.itemsSig().length === 0);

  /** Envío gratis a partir del umbral; con carrito vacío no se cobra. */
  readonly costoEnvio = computed(() => {
    const sub = this.subtotalSig();
    return sub === 0 || sub >= ENVIO.umbralGratis ? 0 : ENVIO.costo;
  });
  readonly total = computed(() => this.subtotalSig() + this.costoEnvio());
  readonly faltaParaEnvioGratis = computed(() =>
    Math.max(0, ENVIO.umbralGratis - this.subtotalSig()),
  );

  /** Sincroniza el estado local. No llama al backend si no hay sesión. */
  refrescar(): void {
    if (!this.auth.autenticado()) {
      this.vaciarLocal();
      return;
    }
    this.obtener().subscribe({ error: () => this.vaciarLocal() });
  }

  /** GET /api/carrito — lo crea vacío si el usuario aún no tenía. */
  obtener(): Observable<Carrito> {
    return this.http.get<Carrito>(RUTAS_COMPRAS.carrito.base).pipe(tap((c) => this.aplicar(c)));
  }

  /**
   * POST /api/carrito/items El backend valida contra catálogo antes de guardar: 404 si
   * el producto no existe, 409 si se pide más de lo que hay en stock.
   */
  agregar(productoId: number, cantidad = 1): Observable<Carrito> {
    const cuerpo: AgregarItemRequest = { productoId, cantidad };
    return this.http
      .post<Carrito>(RUTAS_COMPRAS.carrito.items, cuerpo)
      .pipe(tap((c) => this.aplicar(c)));
  }

  /** PUT /api/carrito/items/{itemId} — 409 si supera el stock disponible. */
  cambiarCantidad(itemId: number, cantidad: number): Observable<Carrito> {
    const cuerpo: CambiarCantidadRequest = { cantidad };
    return this.http
      .put<Carrito>(RUTAS_COMPRAS.carrito.item(itemId), cuerpo)
      .pipe(tap((c) => this.aplicar(c)));
  }

  /**
   * DELETE /api/carrito/items/{itemId} El backend busca por id de ítem Y de carrito a la
   * vez, así que un id ajeno devuelve 404 en vez de borrar el ítem de otro usuario.
   */
  eliminar(itemId: number): Observable<Carrito> {
    return this.http
      .delete<Carrito>(RUTAS_COMPRAS.carrito.item(itemId))
      .pipe(tap((c) => this.aplicar(c)));
  }

  /** DELETE /api/carrito — vacía el carrito entero. */
  vaciar(): Observable<Carrito> {
    return this.http.delete<Carrito>(RUTAS_COMPRAS.carrito.base).pipe(tap((c) => this.aplicar(c)));
  }

  private aplicar(c: Carrito): void {
    this.itemsSig.set(c.items ?? []);
    this.subtotalSig.set(c.subtotal ?? 0);
  }

  private vaciarLocal(): void {
    this.itemsSig.set([]);
    this.subtotalSig.set(0);
  }
}
