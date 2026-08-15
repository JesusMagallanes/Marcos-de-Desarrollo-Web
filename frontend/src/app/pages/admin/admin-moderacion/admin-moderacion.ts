import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ErrorApi, EstadoModeracion, Producto, ProductoService } from '../../../core';

/**
 * Cola de revisión de productos de colaborador.
 *
 * <p>Los de la tienda no aparecen: no pasan por moderación, porque no tendría
 * sentido que el administrador se aprobara a sí mismo.
 */
@Component({
  selector: 'app-admin-moderacion',
  imports: [CurrencyPipe, Cargando],
  templateUrl: './admin-moderacion.html',
})
export class AdminModeracion implements OnInit {
  private productos = inject(ProductoService);

  protected cargando = signal(true);
  protected error = signal('');
  protected lista = signal<Producto[]>([]);
  protected filtro = signal<EstadoModeracion>('PENDIENTE');

  protected resolviendo = signal<number | null>(null);
  protected pidiendoMotivo = signal<number | null>(null);
  protected motivo = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.productos.colaModeracion(this.filtro()).subscribe({
      next: (p) => {
        this.lista.set(p);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected cambiarFiltro(estado: EstadoModeracion): void {
    this.filtro.set(estado);
    this.pidiendoMotivo.set(null);
    this.cargar();
  }

  protected aprobar(p: Producto): void {
    this.resolviendo.set(p.id);
    this.productos.aprobarProducto(p.id).subscribe({
      next: () => {
        this.resolviendo.set(null);
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.resolviendo.set(null);
        this.error.set(e.mensaje);
      },
    });
  }

  protected rechazar(p: Producto): void {
    // El motivo lo lee el colaborador para corregir, así que el backend exige
    // de 10 a 500 caracteres y aquí se avisa antes de mandarlo.
    if (this.motivo().trim().length < 10) {
      this.error.set('Explica el motivo en al menos 10 caracteres: el vendedor lo va a leer.');
      return;
    }

    this.resolviendo.set(p.id);
    this.productos.rechazarProducto(p.id, { motivo: this.motivo().trim() }).subscribe({
      next: () => {
        this.resolviendo.set(null);
        this.pidiendoMotivo.set(null);
        this.motivo.set('');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.resolviendo.set(null);
        this.error.set(e.mensaje);
      },
    });
  }
}
