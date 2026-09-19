import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { CategoriaService, GuiaService } from '../../core';
import { Footer } from './footer';

/**
 * El pie enseña pocas categorías y las que valen: las hojas del árbol.
 * La raíz y las intermedias solo agrupan, y una lista de diecisiete
 * doblaba la altura del pie.
 */
describe('Footer', () => {
  function categoria(id: number, name: string, padreId: number | null) {
    return { id, name, slug: name.toLowerCase(), description: '', icono: null, padreId };
  }

  it('solo pinta las hojas, ordenadas y como mucho ocho', () => {
    const hojas = Array.from({ length: 10 }, (_, i) => categoria(100 + i, `Hoja ${String.fromCharCode(74 - i)}`, 2));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: CategoriaService,
          useValue: {
            listar: () =>
              of([
                categoria(1, 'Tecnología', null), // raíz: no se enseña
                categoria(2, 'Computación', 1), // intermedia: tiene hijas
                categoria(3, 'Prueba 1786163352', null), // otra raíz suelta: tampoco
                ...hojas,
              ]),
          },
        },
        { provide: GuiaService, useValue: { listar: () => of([]) } },
      ],
    });
    const fixture = TestBed.createComponent(Footer);
    fixture.detectChanges();

    const enlaces = Array.from(
      fixture.nativeElement.querySelectorAll('.pie-categorias a') as NodeListOf<HTMLAnchorElement>,
    ).map((a) => a.textContent?.trim());

    expect(enlaces).toHaveLength(8);
    expect(enlaces).not.toContain('Tecnología');
    expect(enlaces).not.toContain('Computación');
    expect(enlaces).not.toContain('Prueba 1786163352');
    expect(enlaces).toEqual([...enlaces].sort((a, b) => a!.localeCompare(b!, 'es')));
  });
});
