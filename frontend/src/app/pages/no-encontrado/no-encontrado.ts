import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-no-encontrado',
  imports: [RouterLink],
  template: `
    <div class="container text-center py-5 my-5">
      <h1 class="display-1 fw-bold text-success">404</h1>
      <h4 class="mb-3">Página no encontrada</h4>
      <p class="text-muted mb-4">La página que buscas no existe o fue movida.</p>
      <a class="btn btn-success" routerLink="/">Volver al inicio</a>
    </div>
  `,
})
export class NoEncontrado {}
