import { Component } from '@angular/core';

@Component({
  selector: 'app-somos',
  template: `
    <div class="container py-5">
      <div class="row align-items-center g-4 mb-5">
        <div class="col-12 col-lg-6">
          <h3 class="fw-bold mb-3">Quiénes somos</h3>
          <p class="text-secondary">
            SmartZone es una tienda peruana de tecnología. Vendemos laptops, monitores, celulares,
            consolas y accesorios de las mejores marcas, con garantía oficial y envíos a todo el país.
          </p>
          <p class="text-secondary mb-0">
            Nuestro objetivo es que comprar tecnología sea simple, seguro y a buen precio.
          </p>
        </div>
        <div class="col-12 col-lg-6">
          <img src="/Img/img.png" alt="SmartZone" class="img-fluid rounded shadow-sm" />
        </div>
      </div>

      <div class="row g-4 text-center">
        @for (v of valores; track v.titulo) {
          <div class="col-12 col-md-4">
            <div class="p-4 h-100 border rounded">
              <i class="fa-solid {{ v.icono }} fs-2 text-success mb-3"></i>
              <h6 class="fw-bold">{{ v.titulo }}</h6>
              <p class="small text-muted mb-0">{{ v.texto }}</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class Somos {
  protected readonly valores = [
    {
      icono: 'fa-shield-halved',
      titulo: 'Productos originales',
      texto: 'Trabajamos solo con distribuidores autorizados y garantía oficial.',
    },
    {
      icono: 'fa-truck-fast',
      titulo: 'Envíos rápidos',
      texto: 'Entregas de 1 a 3 días hábiles en Lima y provincias.',
    },
    {
      icono: 'fa-headset',
      titulo: 'Soporte cercano',
      texto: 'Te acompañamos antes, durante y después de tu compra.',
    },
  ];
}
