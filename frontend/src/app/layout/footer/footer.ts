import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
  private readonly categorias = signal<Categoria[]>([]);

  /** Cuántas caben en el pie sin que la columna se vuelva una lista interminable. */
  static readonly MAXIMO_CATEGORIAS = 8;

  /**
   * Las categorías del pie: solo las hojas del árbol, y como mucho ocho.
   *
   * Antes salían las diecisiete tal cual venían de la API, incluida la raíz
   * «Tecnología» —que no es una categoría a la que ir— y las intermedias
   * («Computación», «Periféricos»), que solo agrupan a otras. La columna era el
   * doble de alta que el resto del pie. Las hojas son las que tienen productos
   * y las que la gente busca; para todo lo demás está el menú de Categorías.
   */
  protected readonly categoriasPie = computed(() => {
    const todas = this.categorias();
    const conHijos = new Set(todas.map((c) => c.padreId).filter((id) => id != null));
    return todas
      .filter((c) => c.padreId != null && !conHijos.has(c.id))
      .sort((a, b) => a.name.localeCompare(b.name, 'es'))
      .slice(0, Footer.MAXIMO_CATEGORIAS);
  });

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
