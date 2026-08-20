import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { DireccionModal } from './direccion-modal';
import {
  DireccionEntrega,
  UbigeoService,
  direccionCompleta,
  direccionEnUnaLinea,
  direccionVacia,
} from '../../core';

/**
 * El ubigeo de verdad son 1874 distritos que vienen de la base. Aquí basta con
 * un puñado: lo que se prueba es la cascada, no el contenido del catálogo.
 */
const ubigeoFalso = {
  departamentos: () => of(['Cusco', 'Lima']),
  provincias: (dep: string) => of(dep === 'Lima' ? ['Huaral', 'Lima'] : ['Cusco']),
  distritos: (_dep: string, pro: string) =>
    of(pro === 'Lima' ? ['Miraflores', 'Surco'] : ['Chinchero']),
};

function completa(): DireccionEntrega {
  return {
    ...direccionVacia(),
    calle: 'Av. Los Próceres',
    numero: '1420',
    referencia: 'Piso 4',
    codigoPostal: '15074',
    distrito: 'Miraflores',
    provincia: 'Lima',
    departamento: 'Lima',
    receptorNombre: 'Ana Vega Ríos',
    telefonoContacto: '987654321',
  };
}

/**
 * Las reglas de qué dirección sirve para entregar. Están aparte del componente
 * porque el backend aplica las mismas: si estas dos se separan, el comprador
 * pasa la validación del navegador y se come un 400 al volver de MercadoPago.
 */
describe('reglas de la dirección', () => {
  it('una dirección completa vale', () => {
    expect(direccionCompleta(completa())).toBe(true);
  });

  it('sin código postal de 5 dígitos no vale', () => {
    // Es lo que usa la pasarela para calcular el envío: sin él no hay entrega.
    expect(direccionCompleta({ ...completa(), codigoPostal: '150' })).toBe(false);
    expect(direccionCompleta({ ...completa(), codigoPostal: 'ABCDE' })).toBe(false);
  });

  it('sin distrito no vale', () => {
    // En Perú es lo que decide el reparto: la misma avenida existe en varios.
    expect(direccionCompleta({ ...completa(), distrito: '  ' })).toBe(false);
  });

  it('el teléfono son 9 dígitos exactos', () => {
    expect(direccionCompleta({ ...completa(), telefonoContacto: '98765432' })).toBe(false);
    expect(direccionCompleta({ ...completa(), telefonoContacto: '987 654 321' })).toBe(false);
  });

  it('la referencia y el punto del mapa son opcionales', () => {
    const sinExtras = { ...completa(), referencia: '', latitud: undefined, longitud: undefined };
    expect(direccionCompleta(sinExtras)).toBe(true);
  });

  it('el resumen se lee como una dirección, no como un volcado de campos', () => {
    expect(direccionEnUnaLinea(completa())).toBe('Av. Los Próceres 1420, Miraflores, Lima');
  });
});

describe('DireccionModal', () => {
  function montar(inicial: DireccionEntrega | null = null) {
    TestBed.configureTestingModule({
      imports: [DireccionModal],
      providers: [{ provide: UbigeoService, useValue: ubigeoFalso }],
    });
    const fixture = TestBed.createComponent(DireccionModal);
    fixture.componentRef.setInput('inicial', inicial);
    fixture.detectChanges();
    return fixture;
  }

  it('se abre con lo que ya había, para editar en vez de empezar de cero', () => {
    const fixture = montar(completa());
    const el = fixture.nativeElement as HTMLElement;

    expect((el.querySelector('#dir-calle') as HTMLInputElement).value).toBe('Av. Los Próceres');
    expect((el.querySelector('#dir-cp') as HTMLInputElement).value).toBe('15074');
    // El distrito ya no se escribe: se elige de la lista que viene de la base.
    expect((el.querySelector('#dir-distrito') as HTMLSelectElement).value).toBe('Miraflores');
  });

  it('no emite una dirección incompleta', () => {
    const fixture = montar();
    const guardado = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardado);

    const el = fixture.nativeElement as HTMLElement;
    [...el.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Usar esta dirección'))!
      .click();
    fixture.detectChanges();

    // Nada sale hacia el checkout, y se explica qué falta en vez de no hacer nada.
    expect(guardado).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Faltan datos');
  });

  it('los errores no salen hasta que se intenta guardar', () => {
    // Pintar el formulario en rojo mientras todavía se escribe el primer campo
    // es regañar al comprador por no haber terminado.
    const el = montar().nativeElement as HTMLElement;

    expect(el.querySelectorAll('.invalid-feedback').length).toBe(0);
  });

  it('con todo relleno, emite la dirección', () => {
    const fixture = montar(completa());
    const guardado = vi.fn();
    fixture.componentInstance.guardar.subscribe(guardado);

    const el = fixture.nativeElement as HTMLElement;
    [...el.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Usar esta dirección'))!
      .click();

    expect(guardado).toHaveBeenCalledOnce();
    expect(guardado.mock.calls[0][0]).toMatchObject({
      calle: 'Av. Los Próceres',
      numero: '1420',
      distrito: 'Miraflores',
      codigoPostal: '15074',
    });
  });

  it('Escape cierra: es lo que todo el mundo intenta primero', () => {
    const fixture = montar();
    const cerrado = vi.fn();
    fixture.componentInstance.cerrar.subscribe(cerrado);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(cerrado).toHaveBeenCalled();
  });
});

/**
 * La cascada. Es lo que impide un distrito que existe dentro de la provincia
 * que no es: hay Miraflores en cinco departamentos distintos, y un pedido con
 * el distrito bueno y la provincia mala acaba en la otra punta del país.
 */
describe('DireccionModal · ubigeo del Perú', () => {
  function montar(inicial: DireccionEntrega | null = null) {
    TestBed.configureTestingModule({
      imports: [DireccionModal],
      providers: [{ provide: UbigeoService, useValue: ubigeoFalso }],
    });
    const fixture = TestBed.createComponent(DireccionModal);
    fixture.componentRef.setInput('inicial', inicial);
    fixture.detectChanges();
    return fixture;
  }

  const opciones = (el: HTMLElement, id: string) =>
    [...el.querySelectorAll(`${id} option`)].map((o) => (o as HTMLOptionElement).value).filter(Boolean);

  function elegir(fixture: ReturnType<typeof montar>, id: string, valor: string) {
    const select = (fixture.nativeElement as HTMLElement).querySelector(id) as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('los departamentos salen de la base, no de una lista escrita a mano', () => {
    const el = montar().nativeElement as HTMLElement;
    expect(opciones(el, '#dir-departamento')).toEqual(['Cusco', 'Lima']);
  });

  it('provincia y distrito están bloqueados hasta elegir el de arriba', () => {
    const el = montar().nativeElement as HTMLElement;

    expect((el.querySelector('#dir-provincia') as HTMLSelectElement).disabled).toBe(true);
    expect((el.querySelector('#dir-distrito') as HTMLSelectElement).disabled).toBe(true);
  });

  it('elegir departamento carga sus provincias', () => {
    const fixture = montar();
    elegir(fixture, '#dir-departamento', 'Lima');

    expect(opciones(fixture.nativeElement, '#dir-provincia')).toEqual(['Huaral', 'Lima']);
  });

  it('cambiar de departamento borra lo que había debajo', () => {
    // Sin esto queda «Cusco» con la provincia «Lima» todavía seleccionada: una
    // combinación que no existe y que el usuario no ve como un error.
    const fixture = montar();
    elegir(fixture, '#dir-departamento', 'Lima');
    elegir(fixture, '#dir-provincia', 'Lima');
    elegir(fixture, '#dir-distrito', 'Miraflores');

    elegir(fixture, '#dir-departamento', 'Cusco');

    const el = fixture.nativeElement as HTMLElement;
    expect((el.querySelector('#dir-provincia') as HTMLSelectElement).value).toBe('');
    expect((el.querySelector('#dir-distrito') as HTMLSelectElement).value).toBe('');
  });

  it('fuera de Perú se escribe a mano y se pide el país', () => {
    const fixture = montar();
    const el = fixture.nativeElement as HTMLElement;

    (el.querySelector('#dir-fuera') as HTMLInputElement).dispatchEvent(new Event('change'));
    fixture.detectChanges();

    // El catálogo del INEI solo cubre Perú: fuera no hay lista que ofrecer.
    expect(el.querySelector('#dir-departamento')).toBeNull();
    expect(el.querySelector('#dir-departamento-libre')).not.toBeNull();
    expect(el.querySelector('#dir-pais-codigo')).not.toBeNull();
  });
});
