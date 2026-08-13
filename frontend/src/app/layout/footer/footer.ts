import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Categoria, CategoriaService, GuiaResumen, GuiaService } from '../../core';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer implements OnInit {
  private categoriaService = inject(CategoriaService);
  private guiaService = inject(GuiaService);

  protected readonly anio = new Date().getFullYear();
  protected readonly categorias = signal<Categoria[]>([]);

  /**
   * "Aprende con nosotros". Antes eran dos enlaces escritos a mano que no
   * llevaban a ninguna parte; ahora salen de las guías que publica el
   * administrador, igual que las categorías de la columna de al lado.
   */
  protected readonly guias = signal<GuiaResumen[]>([]);

  ngOnInit(): void {
    this.categoriaService.listar().subscribe({
      next: (cats) => this.categorias.set(cats),
      error: () => this.categorias.set([]),
    });

    // Si falla, la columna se queda vacía y ya está: el pie no debe romper la
    // página por no poder pintar unos enlaces de ayuda.
    this.guiaService.listar().subscribe({
      next: (guias) => this.guias.set(guias),
      error: () => this.guias.set([]),
    });
  }
}
