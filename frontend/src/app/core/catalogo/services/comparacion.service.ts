import { Injectable, computed, signal } from '@angular/core';

const CLAVE = 'sz_comparar';
export const MAX_COMPARAR = 3;

/** Lo mínimo para pintar la barra sin pedir nada al servidor. */
export interface ProductoAComparar {
  id: number;
  name: string;
  imageUrl: string | null;
}

/**
 * Los productos que el cliente va eligiendo para comparar.
 *
 * Vive en el navegador (localStorage) y no en el servidor: es una lista de
 * trabajo de unos minutos, no un dato de la cuenta, y así funciona sin sesión.
 * Como mucho tres, que es lo que se puede leer en una tabla.
 */
@Injectable({ providedIn: 'root' })
export class ComparacionService {
  private readonly lista = signal<ProductoAComparar[]>(leer());

  readonly productos = this.lista.asReadonly();
  readonly ids = computed(() => this.lista().map((p) => p.id));
  readonly llena = computed(() => this.lista().length >= MAX_COMPARAR);

  contiene(id: number): boolean {
    return this.ids().includes(id);
  }

  /** Añade o quita. Devuelve false si estaba llena y no se pudo añadir. */
  alternar(producto: ProductoAComparar): boolean {
    if (this.contiene(producto.id)) {
      this.guardar(this.lista().filter((p) => p.id !== producto.id));
      return true;
    }
    if (this.llena()) {
      return false;
    }
    this.guardar([...this.lista(), { id: producto.id, name: producto.name, imageUrl: producto.imageUrl }]);
    return true;
  }

  quitar(id: number): void {
    this.guardar(this.lista().filter((p) => p.id !== id));
  }

  vaciar(): void {
    this.guardar([]);
  }

  private guardar(lista: ProductoAComparar[]): void {
    this.lista.set(lista);
    try {
      localStorage.setItem(CLAVE, JSON.stringify(lista));
    } catch {
      // Sin almacenamiento, vive solo en memoria.
    }
  }
}

function leer(): ProductoAComparar[] {
  try {
    const crudo = JSON.parse(localStorage.getItem(CLAVE) ?? '[]');
    return Array.isArray(crudo)
      ? crudo.filter((p) => typeof p?.id === 'number' && typeof p?.name === 'string').slice(0, MAX_COMPARAR)
      : [];
  } catch {
    return [];
  }
}
