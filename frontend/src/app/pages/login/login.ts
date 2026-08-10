import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core';

/**
 * El login ya no es una página: es un modal global (`app-login-modal`) que se abre
 * sobre la tienda. Esta ruta sigue existiendo porque el backend (flujo OAuth) y el
 * interceptor redirigen a `/login` con parámetros; al aterrizar aquí se abre el modal
 * solo con esos parámetros y la tienda queda detrás.
 */
@Component({
  selector: 'app-login',
  template: '',
})
export class Login implements OnInit {
  private auth = inject(AuthService);
  private ruta = inject(ActivatedRoute);

  ngOnInit(): void {
    const q = this.ruta.snapshot.queryParamMap;
    this.auth.abrirLogin({
      modo: q.get('modo') === 'registro' ? 'registro' : 'login',
      error: q.get('error') ?? undefined,
      expirado: q.get('expirado') === 'true',
      redirigir: q.get('redirigir') ?? undefined,
    });
  }
}
