import { Component } from '@angular/core';

@Component({
  selector: 'app-metodos-pago',
  template: `
    <div class="container py-5">
      <h3 class="fw-bold mb-2">Métodos de pago</h3>
      <p class="text-secondary mb-5">Elige la forma que más te convenga. Todos los pagos son seguros.</p>

      <div class="row g-4">
        @for (m of metodos; track m.titulo) {
          <div class="col-12 col-md-6">
            <div class="d-flex gap-3 p-4 border rounded h-100">
              <img [src]="m.icono" [alt]="m.titulo" height="40" />
              <div>
                <h6 class="fw-bold mb-1">{{ m.titulo }}</h6>
                <p class="small text-muted mb-0">{{ m.detalle }}</p>
              </div>
            </div>
          </div>
        }
      </div>

      <div class="mt-5 p-4 border rounded bg-light">
        <h6 class="fw-bold mb-2"><i class="fa-solid fa-lock me-2 text-success"></i>Compra protegida</h6>
        <p class="small text-muted mb-0">
          Procesamos los pagos mediante MercadoPago. SmartZone no almacena los datos de tu tarjeta.
        </p>
      </div>
    </div>
  `,
})
export class MetodosPago {
  protected readonly metodos = [
    {
      icono: '/Img/visa-icon.svg',
      titulo: 'Tarjetas de crédito y débito',
      detalle: 'Visa, Mastercard y American Express. Hasta 12 cuotas sin intereses.',
    },
    {
      icono: '/Img/yape-icon.svg',
      titulo: 'Yape y Plin',
      detalle: 'Paga al instante desde tu celular escaneando el código QR.',
    },
    {
      icono: '/Img/paypal-icon.svg',
      titulo: 'PayPal',
      detalle: 'Ideal si compras desde el extranjero.',
    },
    {
      icono: '/Img/pefectivo-icon.svg',
      titulo: 'Pago en efectivo',
      detalle: 'Contra entrega, disponible en Lima Metropolitana.',
    },
  ];
}
