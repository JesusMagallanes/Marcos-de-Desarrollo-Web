import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FacetasCatalogo, FiltroCatalogo, filtroVacio } from '../../core';
import { FiltrosCatalogo } from './filtros-catalogo';

const FACETAS: FacetasCatalogo = {
  total: 30,
  disponibles: 28,
  marcas: [
    { id: 1, nombre: 'Lenovo', conteo: 20 },
    { id: 2, nombre: 'ASUS', conteo: 10 },
  ],
  atributos: [
    {
      codigo: 'ram_gb',
      nombre: 'RAM',
      valores: [
        { valor: '16', conteo: 18 },
        { valor: '32', conteo: 12 },
      ],
    },
  ],
};

@Component({
  imports: [FiltrosCatalogo],
  template: `<app-filtros-catalogo
    [facetas]="facetas"
    [(filtro)]="filtro"
    (atributoAplicado)="aplicados.push($event)"
  />`,
})
class Anfitrion {
  facetas = FACETAS;
  filtro = signal<FiltroCatalogo>(filtroVacio());
  aplicados: { codigo: string; valor: string }[] = [];
}

describe('FiltrosCatalogo', () => {
  let host: Anfitrion;
  let el: HTMLElement;
  let fixture: ComponentFixture<Anfitrion>;

  function montar() {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    host = fixture.componentInstance;
    el = fixture.nativeElement;
    return fixture;
  }

  function casillas(): HTMLInputElement[] {
    return Array.from(el.querySelectorAll('input[type="checkbox"]'));
  }

  beforeEach(() => montar());

  it('pinta una casilla por marca y por valor de atributo', () => {
    const etiquetas = Array.from(el.querySelectorAll('.opcion-filtro span:not(.conteo)')).map(
      (s) => s.textContent?.trim(),
    );
    expect(etiquetas).toContain('Lenovo');
    expect(etiquetas).toContain('ASUS');
    expect(etiquetas).toContain('16');
    expect(etiquetas).toContain('32');
  });

  it('marcar una marca produce un filtro NUEVO con esa marca', () => {
    const anterior = host.filtro();
    casillas()[0].click(); // primera marca (Lenovo)

    expect(host.filtro().marcaIds).toEqual([1]);
    // Objeto nuevo, no mutación del anterior: así el padre detecta el cambio.
    expect(host.filtro()).not.toBe(anterior);
  });

  it('dos valores del mismo atributo se acumulan (OR dentro del código)', () => {
    // Las dos casillas de RAM: 16 y 32.
    const ram16 = casillas().find((c) => c.closest('.opcion-filtro')?.textContent?.includes('16'))!;
    const ram32 = casillas().find((c) => c.closest('.opcion-filtro')?.textContent?.includes('32'))!;
    ram16.click();
    ram32.click();

    expect(host.filtro().atributos).toEqual(['ram_gb:16', 'ram_gb:32']);
  });

  it('aplicar un atributo emite el evento; quitarlo NO', () => {
    const ram16 = casillas().find((c) => c.closest('.opcion-filtro')?.textContent?.includes('16'))!;
    ram16.click();
    expect(host.aplicados).toEqual([{ codigo: 'ram_gb', valor: '16' }]);

    ram16.click(); // quitar
    expect(host.filtro().atributos).toEqual([]);
    // Quitar no vuelve a emitir: dejar de filtrar no es una preferencia.
    expect(host.aplicados).toHaveLength(1);
  });

  it('el orden viaja al filtro', () => {
    const select: HTMLSelectElement = el.querySelector('select[id^="ordenSel"]')!;
    select.value = 'precio-asc';
    select.dispatchEvent(new Event('change'));

    expect(host.filtro().orden).toBe('precio-asc');
  });

  it('limpiar borra los filtros pero respeta el orden elegido', () => {
    host.filtro.set({
      ...filtroVacio(),
      marcaIds: [1, 2],
      atributos: ['ram_gb:16'],
      precioMin: 100,
      soloDisponibles: true,
      orden: 'precio-desc',
    });
    fixture.detectChanges();

    const limpiar: HTMLButtonElement = el.querySelector('.btn-link')!;
    limpiar.click();

    const f = host.filtro();
    expect(f.marcaIds).toEqual([]);
    expect(f.atributos).toEqual([]);
    expect(f.precioMin).toBeNull();
    expect(f.soloDisponibles).toBe(false);
    // El orden es preferencia de lectura, no filtro: se conserva.
    expect(f.orden).toBe('precio-desc');
  });
});
