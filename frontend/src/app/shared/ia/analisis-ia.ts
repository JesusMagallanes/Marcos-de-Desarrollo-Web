import { Component, computed, inject, input, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { AnalisisModeracion, ColaboradorService, ErrorApi, IaService } from '../../core';

/**
 * «Analizar con IA» para quien modera: productos, reseñas y solicitudes.
 *
 * Se pide a demanda, con un botón, y no al abrir la bandeja: cada análisis es
 * una llamada al proveedor, y la mayoría de lo que hay en una bandeja se
 * resuelve sin necesitarlo. El resultado ORIENTA —riesgo, motivos, una
 * sugerencia— y los botones de aprobar y rechazar siguen donde estaban: la IA
 * no decide nada.
 *
 * Un solo componente para las tres bandejas porque el backend devuelve la misma
 * forma en las tres; quien lo usa solo dice qué analizar.
 *
 * Sin modelo configurado el botón sigue ahí: el backend responde con las reglas
 * deterministas (contacto externo, precio anómalo, intento de manipulación) y
 * el resultado lo dice.
 */
@Component({
  selector: 'app-analisis-ia',
  templateUrl: './analisis-ia.html',
  styleUrl: './analisis-ia.css',
})
export class AnalisisIa {
  readonly tipo = input.required<'producto' | 'valoracion' | 'solicitud'>();
  readonly id = input.required<number>();

  private readonly ia = inject(IaService);
  private readonly colaboradores = inject(ColaboradorService);

  protected readonly cargando = signal(false);
  protected readonly analisis = signal<AnalisisModeracion | null>(null);
  protected readonly error = signal('');

  protected readonly clase = computed(() => {
    switch (this.analisis()?.riesgo) {
      case 'ALTO':
        return 'text-bg-danger';
      case 'MEDIO':
        return 'text-bg-warning';
      default:
        return 'text-bg-success';
    }
  });

  private peticion(): Observable<AnalisisModeracion> {
    switch (this.tipo()) {
      case 'producto':
        return this.ia.analizarProducto(this.id());
      case 'valoracion':
        return this.ia.analizarValoracion(this.id());
      case 'solicitud':
        return this.colaboradores.analizar(this.id());
    }
  }

  protected analizar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.peticion().subscribe({
      next: (a) => {
        this.analisis.set(a);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.limitado ? 'Demasiados análisis seguidos. Espera un minuto.' : e.mensaje);
        this.cargando.set(false);
      },
    });
  }
}
