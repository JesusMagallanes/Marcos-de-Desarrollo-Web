import { Component } from '@angular/core';

@Component({
  selector: 'app-canales',
  template: `
    <div class="container py-5">
      <h3 class="fw-bold mb-2">Canales de atención</h3>
      <p class="text-secondary mb-5">Estamos disponibles por estos medios.</p>

      <div class="row g-4">
        @for (c of canales; track c.titulo) {
          <div class="col-12 col-md-6 col-lg-3">
            <div class="p-4 h-100 border rounded text-center">
              <i class="fa-solid {{ c.icono }} fs-2 text-success mb-3"></i>
              <h6 class="fw-bold">{{ c.titulo }}</h6>
              <p class="small text-muted mb-2">{{ c.detalle }}</p>
              <a class="small" [href]="c.enlace">{{ c.accion }}</a>
            </div>
          </div>
        }
      </div>

      <div class="mt-5 p-4 border rounded">
        <h6 class="fw-bold mb-3">Horario de atención</h6>
        <p class="mb-1 small">Lunes a viernes: 9:00 a. m. – 6:00 p. m.</p>
        <p class="mb-0 small">Sábados: 9:00 a. m. – 1:00 p. m.</p>
      </div>
    </div>
  `,
})
export class Canales {
  protected readonly canales = [
    {
      icono: 'fa-phone',
      titulo: 'Teléfono',
      detalle: '+51 987 654 321',
      enlace: 'tel:+51987654321',
      accion: 'Llamar',
    },
    {
      icono: 'fa-whatsapp',
      titulo: 'WhatsApp',
      detalle: 'Respuesta en minutos',
      enlace: 'https://wa.me/51987654321',
      accion: 'Chatear',
    },
    {
      icono: 'fa-envelope',
      titulo: 'Correo',
      detalle: 'soporte@smartzone.com',
      enlace: 'mailto:soporte@smartzone.com',
      accion: 'Escribir',
    },
    {
      icono: 'fa-location-dot',
      titulo: 'Tienda',
      detalle: 'Lima, Perú',
      enlace: 'https://maps.google.com',
      accion: 'Ver mapa',
    },
  ];
}
