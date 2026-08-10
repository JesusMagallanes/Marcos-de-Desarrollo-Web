import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Categoria, CategoriaService } from '../../core';

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer implements OnInit {
  private categoriaService = inject(CategoriaService);
  protected readonly anio = new Date().getFullYear();
  protected readonly categorias = signal<Categoria[]>([]);

  ngOnInit(): void {
    this.categoriaService.listar().subscribe({
      next: (cats) => this.categorias.set(cats),
      error: () => this.categorias.set([]),
    });
  }
}
