import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  ErrorApi,
  ETIQUETAS_VALORACION,
  EstadoPeticion,
  EstadoValoracion,
  ValoracionAdmin,
  ValoracionService,
} from '../../../core';

const ESTRELLAS = [1, 2, 3, 4, 5] as const;

type Seccion = 'TODAS' | EstadoValoracion;

/**
 * Moderación de valoraciones: ninguna reseña se publica en la tienda hasta que
 * el administrador la aprueba. Este panel es la cola de revisión.
 */
@Component({
  selector: 'app-admin-valoraciones',
  imports: [DatePipe],
  templateUrl: './admin-valoraciones.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminValoraciones implements OnInit, OnDestroy {
  private valoracionService = inject(ValoracionService);

  protected estado = new EstadoPeticion();
  protected valoraciones = signal<ValoracionAdmin[]>([]);
  protected seccion = signal<Seccion>('PENDIENTE');
  protected filtroTexto = signal('');
  protected procesando = signal<Set<number>>(new Set());
  protected confirmandoId = signal<number | null>(null);

  protected readonly estrellas = ESTRELLAS;
  protected readonly etiquetas = ETIQUETAS_VALORACION;
  protected readonly secciones: readonly Seccion[] = ['PENDIENTE', 'APROBADA', 'RECHAZADA', 'TODAS'];

  protected conteos = computed(() => {
    const conteo: Record<Seccion, number> = {
      TODAS: 0,
      PENDIENTE: 0,
      APROBADA: 0,
      RECHAZADA: 0,
    };
    for (const v of this.valoraciones()) {
      conteo[v.estado]++;
      conteo.TODAS++;
    }
    return conteo;
  });

  protected visibles = computed(() => {
    const seccion = this.seccion();
    const q = this.filtroTexto().trim().toLowerCase();
    return this.valoraciones().filter((v) => {
      if (seccion !== 'TODAS' && v.estado !== seccion) return false;
      if (
        q &&
        !v.productoNombre.toLowerCase().includes(q) &&
        !v.nombre.toLowerCase().includes(q) &&
        !v.comentario.toLowerCase().includes(q)
      ) {
        return false;
      }
      return true;
    });
  });

  protected clases: Record<EstadoValoracion, string> = {
    PENDIENTE: 'bg-warning text-dark',
    APROBADA: 'bg-success',
    RECHAZADA: 'bg-danger',
  };

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.valoracionService.listarAdmin().subscribe({
      next: (lista) => {
        this.valoraciones.set(lista);
        this.confirmandoId.set(null);
        this.estado.exito();
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  protected aprobar(v: ValoracionAdmin): void {
    this.cambiarEstado(v, 'APROBADA');
  }

  protected rechazar(v: ValoracionAdmin): void {
    this.cambiarEstado(v, 'RECHAZADA');
  }

  protected cambiarEstado(v: ValoracionAdmin, siguiente: EstadoValoracion): void {
    this.procesando.update((s) => {
      const copia = new Set(s);
      copia.add(v.id);
      return copia;
    });
    this.estado.limpiarError();

    this.valoracionService.cambiarEstado(v.id, siguiente).subscribe({
      next: () => {
        this.procesando.update((s) => {
          const copia = new Set(s);
          copia.delete(v.id);
          return copia;
        });
        this.estado.mostrarAviso(
          siguiente === 'APROBADA' ? 'Valoración aprobada.' : 'Valoración rechazada.',
        );
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.procesando.update((s) => {
          const copia = new Set(s);
          copia.delete(v.id);
          return copia;
        });
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(v: ValoracionAdmin): void {
    this.confirmandoId.set(v.id);
    this.estado.limpiarError();
  }

  protected confirmarEliminar(): void {
    const id = this.confirmandoId();
    if (id == null) return;
    this.procesando.update((s) => {
      const copia = new Set(s);
      copia.add(id);
      return copia;
    });

    this.valoracionService.eliminarAdmin(id).subscribe({
      next: () => {
        this.procesando.update((s) => {
          const copia = new Set(s);
          copia.delete(id);
          return copia;
        });
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Valoración eliminada.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.procesando.update((s) => {
          const copia = new Set(s);
          copia.delete(id);
          return copia;
        });
        this.confirmandoId.set(null);
        this.estado.fallo(e);
      },
    });
  }

  protected ocupada(id: number): boolean {
    return this.procesando().has(id);
  }
}
