import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})
export class Admin {
  protected readonly secciones = [
    { ruta: 'productos', icono: 'fa-box', etiqueta: 'Productos' },
    { ruta: 'descuentos', icono: 'fa-percent', etiqueta: 'Descuentos' },
    { ruta: 'categorias', icono: 'fa-layer-group', etiqueta: 'Categorías' },
    { ruta: 'marcas', icono: 'fa-tag', etiqueta: 'Marcas' },
    { ruta: 'usuarios', icono: 'fa-users', etiqueta: 'Usuarios' },
    { ruta: 'metodos-pago', icono: 'fa-credit-card', etiqueta: 'Métodos de pago' },
    { ruta: 'guias', icono: 'fa-book-open', etiqueta: 'Guías de ayuda' },
  ];
}
