import { Route } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from './app.routes';
import { SECCIONES_ADMIN } from './core';

/**
 * El menú del panel se pinta desde `SECCIONES_ADMIN` y las rutas se declaran
 * aparte, en `app.routes`. Nada obliga a que las dos listas coincidan: cuando no
 * lo hacen, la entrada del menú se ve, se pulsa, y lleva al 404.
 *
 * <p>Es justo lo que pasó con «Solicitudes de venta» y «Revisar productos», y no
 * lo detectó nadie hasta que un compañero se topó con la página de "no
 * encontrado". Por eso se comprueba aquí y no a ojo.
 */
describe('rutas del panel admin', () => {
  const admin = routes.find((r) => r.path === 'admin');
  const hijas = (admin?.children ?? []) as Route[];

  it('el panel existe y tiene secciones', () => {
    expect(admin).toBeDefined();
    expect(hijas.length).toBeGreaterThan(0);
  });

  it('cada entrada del menú lleva a una ruta de verdad', () => {
    const declaradas = new Set(hijas.map((r) => r.path));
    const huerfanas = SECCIONES_ADMIN.filter((s) => !declaradas.has(s.ruta)).map((s) => s.etiqueta);

    // Si esto falla, hay un enlace del menú que acaba en el 404.
    expect(huerfanas).toEqual([]);
  });

  it('ninguna sección se carga sin comprobar el permiso', () => {
    // El backend manda, pero enseñar una pantalla que luego responde 403 en cada
    // llamada es una forma rara de decir que no.
    const sinGuarda = hijas
      .filter((r) => r.loadComponent && !(r.canActivate ?? []).length)
      .map((r) => r.path);

    expect(sinGuarda).toEqual([]);
  });

  it('la entrada del panel redirige según el permiso, no a «productos» a secas', () => {
    const inicio = hijas.find((r) => r.path === '');
    expect(typeof inicio?.redirectTo).toBe('function');
  });
});

/**
 * Angular resuelve `redirectTo` ANTES que las guardias; una ruta con las dos
 * cosas es un error de configuración (NG04014) que impide arrancar la app con
 * `ng serve` y que el build de producción no valida. Pasó con /admin: en
 * desarrollo la aplicación no cargaba y en producción la guardia nunca corría.
 */
describe('configuración de rutas', () => {
  const aplanar = (lista: Route[], prefijo = ''): { ruta: string; r: Route }[] =>
    lista.flatMap((r) => {
      const ruta = `${prefijo}/${r.path ?? ''}`;
      return [{ ruta, r }, ...aplanar((r.children ?? []) as Route[], ruta)];
    });

  it('ninguna ruta combina redirectTo con una guardia', () => {
    const invalidas = aplanar(routes)
      .filter(({ r }) => r.redirectTo !== undefined && (r.canActivate ?? []).length > 0)
      .map(({ ruta }) => ruta);

    expect(invalidas).toEqual([]);
  });
});
