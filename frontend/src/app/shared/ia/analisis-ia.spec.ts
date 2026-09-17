import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { errorInterceptor } from '../../core';
import { AnalisisIa } from './analisis-ia';

@Component({
  imports: [AnalisisIa],
  template: `<app-analisis-ia tipo="producto" [id]="12" /><app-analisis-ia tipo="solicitud" [id]="3" />`,
})
class Anfitrion {}

describe('AnalisisIa', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function montar() {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    http.match('/api/ia/estado').forEach((r) => r.flush({ disponible: true }));
    const botones = fixture.nativeElement.querySelectorAll('app-analisis-ia button') as NodeListOf<HTMLButtonElement>;
    return { fixture, el: fixture.nativeElement as HTMLElement, botones };
  }

  it('no analiza hasta que se pide: cada análisis cuesta una llamada', () => {
    montar();
    http.expectNone((r) => r.url.includes('/moderacion/') || r.url.includes('/analisis-ia'));
  });

  it('pide al endpoint de cada tipo y enseña riesgo, motivos y si hubo IA', () => {
    const { fixture, el, botones } = montar();

    botones[0].click();
    http.expectOne('/api/ia/moderacion/productos/12').flush({
      riesgo: 'MEDIO',
      motivos: ['Incluye datos de contacto.'],
      sugerencia: 'Pedir que los quite.',
      conIa: false,
    });
    botones[1].click();
    http.expectOne('/api/colaboradores/solicitudes/3/analisis-ia').flush({
      riesgo: 'BAJO',
      motivos: [],
      sugerencia: 'Coherente.',
      conIa: true,
    });
    fixture.detectChanges();

    const texto = el.textContent ?? '';
    expect(texto).toContain('Riesgo medio');
    expect(texto).toContain('Incluye datos de contacto.');
    expect(texto).toContain('Solo reglas');
    expect(texto).toContain('IA + reglas');
    // Los botones de decidir no son de este componente: aquí no hay aprobar ni rechazar.
    expect(texto).not.toMatch(/Aprobar|Rechazar/);
  });
});
