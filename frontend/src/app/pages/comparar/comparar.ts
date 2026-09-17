import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Cargando } from '../../shared/cargando/cargando';
import { ImagenCaida } from '../../shared/imagen/imagen-caida';
import { Comparacion, ComparacionService, ErrorApi, IaService, imagenDe } from '../../core';

/**
 * Comparar 2 o 3 productos: la tabla con los datos reales y, con IA, una
 * explicación de las diferencias para el uso que diga la persona.
 *
 * Los ids van en la URL (`/comparar?ids=1,2,3`) para que la comparación se
 * pueda compartir o recargar.
 */
@Component({
  selector: 'app-comparar',
  imports: [RouterLink, Cargando, ImagenCaida],
  templateUrl: './comparar.html',
  styleUrl: './comparar.css',
})
export class Comparar {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly iaService = inject(IaService);
  protected readonly ia = this.iaService;
  protected readonly lista = inject(ComparacionService);
  protected readonly imagenDe = imagenDe;

  protected readonly cargando = signal(true);
  protected readonly explicando = signal(false);
  protected readonly comparacion = signal<Comparacion | null>(null);
  protected readonly error = signal('');
  protected readonly uso = signal('');
  private ids: number[] = [];

  constructor() {
    this.ruta.queryParamMap.subscribe((q) => {
      this.ids = (q.get('ids') ?? '')
        .split(',')
        .map(Number)
        .filter((n) => Number.isInteger(n) && n > 0)
        .slice(0, 3);
      if (this.ids.length < 2) {
        this.cargando.set(false);
        this.comparacion.set(null);
        this.error.set('Elige al menos dos productos para comparar.');
        return;
      }
      this.pedir(false);
    });
  }

  /** Con uso, la IA explica las diferencias para ese uso. */
  protected explicar(): void {
    this.pedir(true);
  }

  protected quitar(id: number): void {
    this.lista.quitar(id);
    this.router.navigate([], { queryParams: { ids: this.ids.filter((x) => x !== id).join(',') } });
  }

  private pedir(conUso: boolean): void {
    if (conUso) {
      this.explicando.set(true);
    } else {
      this.cargando.set(true);
    }
    this.error.set('');
    this.iaService.comparar(this.ids, conUso ? this.uso() : undefined).subscribe({
      next: (c) => {
        this.comparacion.set(c);
        this.cargando.set(false);
        this.explicando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.noEncontrado ? 'Alguno de los productos ya no está disponible.' : e.mensaje);
        this.cargando.set(false);
        this.explicando.set(false);
      },
    });
  }
}
