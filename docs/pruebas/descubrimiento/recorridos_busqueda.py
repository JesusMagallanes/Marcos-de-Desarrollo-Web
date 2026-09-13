"""Recorridos E2E de BUSQUEDA y CATEGORIA, contra la pila completa y base aislada.

Dos flujos reales, con un Chromium propio que se cierra solo:

  1. Inicio -> buscar -> filtrar -> ordenar -> paginar -> ficha -> carrito
  2. Categoria -> filtrar -> ordenar -> paginar -> ficha

Lo que se defiende es que el trabajo lo hace el SERVIDOR: el filtro viaja en la
peticion (`precioMin`, `marcaId`, `atributo`, `orden`, `page`), el total es el
del conjunto entero y no el de la pagina, el orden llega ya ordenado de la base,
y los filtros quedan en la URL para poder compartirla. No se fijan listas
exactas de productos —eso se rompe con cualquier alta— sino invariantes.

Reusa la infraestructura del E2E de descubrimiento: el cliente CDP de `cdp.py`,
las mismas ayudas de base (`sql`, `uno`) y el mismo aislamiento. No instala nada
ni fabrica ninguna firma. No hace falta pago real: el carrito se comprueba
anadiendo un producto y viendo el contador, no pagando.
"""

import json
import os
import shutil
import sys
import time

# La salida lleva caracteres de recuadro; bajo redireccion a fichero la consola
# de Windows es cp1252 y reventaria. Se fuerza UTF-8 pase lo que pase.
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp import Navegador
from visitantes import sql, uno, _poner_formulario, _env  # noqa: reuso de ayudas versionadas

AQUI = os.path.dirname(os.path.abspath(__file__))
FRONT = "http://localhost:14200"
SLUG = "e2e-buscar"
TOKEN = "ZQBUSCA"        # palabra unica, para que la busqueda no traiga catalogo ajeno
COD_RAM = "e2e_ram"
LOTE = 2                 # aqui no se miden impresiones; basta con dejar pintar

pasos = []


def anota(titulo, ok, detalle=""):
    pasos.append((titulo, ok, detalle))
    print("  %s | %s%s" % ("OK " if ok else "FALLA", titulo,
                           (" | " + detalle) if detalle else ""))


# ══════════════ Datos de apoyo: solo dentro de la base aislada ══════════════

def preparar():
    """Una categoria propia con 15 productos, tres marcas y un atributo.

    Precios y atributos variados para que el filtro, el orden y las facetas
    tengan de que agarrarse; 15 productos para que la paginacion (12 por pagina)
    tenga una segunda pagina. Todo con el prefijo del token, dentro del contenedor.
    """
    sql("""
        INSERT INTO catalogo.categoria (name, slug, description)
        VALUES ('e2e-buscar', 'e2e-buscar', 'E2E') ON CONFLICT (slug) DO NOTHING;
    """)
    cat = int(uno("SELECT id FROM catalogo.categoria WHERE slug = 'e2e-buscar'"))

    marcas = {}
    for nombre in ("E2E-Acme", "E2E-Bolt", "E2E-Cyan"):
        sql("INSERT INTO catalogo.marca (name, descripcion) VALUES ('%s', 'E2E') "
            "ON CONFLICT (name) DO NOTHING;" % nombre)
        marcas[nombre] = int(uno("SELECT id FROM catalogo.marca WHERE name = '%s'" % nombre))

    sql("INSERT INTO catalogo.atributo (codigo, nombre, tipo, filtrable) "
        "VALUES ('%s', 'RAM E2E', 'TEXTO', TRUE) ON CONFLICT (codigo) DO NOTHING;" % COD_RAM)
    atr = int(uno("SELECT id FROM catalogo.atributo WHERE codigo = '%s'" % COD_RAM))

    orden_marcas = list(marcas.values())
    rams = ["8", "16", "32"]
    # 30 productos: la busqueda pagina a 24, asi que hacen falta mas de 24 para
    # que haya una segunda pagina que recorrer.
    for i in range(1, 31):
        nombre = "%s monitor %02d" % (TOKEN, i)
        marca = orden_marcas[i % 3]
        precio = 100 + i * 10          # 110 .. 250, todos distintos
        sql("""
            INSERT INTO catalogo.producto
                   (name, description, precio, stock, categoria_id, marca_id, estado_moderacion)
            SELECT '%s', 'E2E', %d.00, 10, %d, %d, 'APROBADO'
             WHERE NOT EXISTS (SELECT 1 FROM catalogo.producto WHERE name = '%s');
        """ % (nombre, precio, cat, marca, nombre))
        pid = int(uno("SELECT id FROM catalogo.producto WHERE name = '%s'" % nombre))
        sql("INSERT INTO catalogo.producto_atributo (producto_id, atributo_id, valor) "
            "VALUES (%d, %d, '%s') ON CONFLICT DO NOTHING;" % (pid, atr, rams[i % 3]))

    total = int(uno("SELECT COUNT(*) FROM catalogo.producto WHERE categoria_id = %d "
                    "AND estado_moderacion = 'APROBADO'" % cat))
    acme = int(uno("SELECT COUNT(*) FROM catalogo.producto p JOIN catalogo.marca m "
                   "ON m.id = p.marca_id WHERE p.categoria_id = %d AND m.name = 'E2E-Acme'" % cat))
    return cat, total, acme


# ══════════════ Un navegador con lo justo para estos flujos ══════════════

class Cliente:

    _puerto = 9260

    def __init__(self, nombre):
        Cliente._puerto += 1
        self.nav = Navegador(perfil=os.path.join(AQUI, ".perfiles", nombre),
                             puerto=Cliente._puerto)
        self.nav.ir(FRONT + "/")
        self.nav.esperar("document.body && document.body.innerText.length > 50", 60)
        self.nav.evaluar("localStorage.clear()")

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.nav.cerrar()

    def espiar(self):
        # fetch Y XmlHttpRequest: Angular HttpClient usa XHR salvo `withFetch`,
        # asi que espiar solo fetch podria no ver ninguna peticion.
        self.nav.evaluar("""
          if (!window.__urls) { window.__urls = [];
            const f = window.fetch;
            window.fetch = function (...a) { try { window.__urls.push(String(a[0])); } catch(e){}
              return f.apply(this, a); };
            const o = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function (m, u) {
              try { window.__urls.push(String(u)); } catch(e){} return o.apply(this, arguments); };
          }
        """)

    def urls(self):
        return json.loads(self.nav.evaluar("JSON.stringify(window.__urls || [])"))

    def ir(self, ruta, listo):
        self.nav.ir(FRONT + ruta)
        self.nav.esperar(listo, 40)
        time.sleep(LOTE)

    def query(self):
        return self.nav.evaluar("location.search")

    # ── lectura de la rejilla (sirve para /buscar y /categoria) ──
    def ids(self):
        return json.loads(self.nav.evaluar("""
          (() => { const v = [];
            document.querySelectorAll('a[href^="/producto/"]').forEach(a => {
              const id = Number(a.getAttribute('href').split('/').pop());
              if (id && !v.includes(id)) v.push(id); });
            return JSON.stringify(v); })()
        """))

    def precios(self):
        return json.loads(self.nav.evaluar("""
          JSON.stringify([...document.querySelectorAll('.pc-precio-actual')]
            .map(e => parseFloat(e.textContent.replace(/[^0-9.]/g, ''))))
        """))

    def total_texto(self):
        return self.nav.evaluar("document.body.innerText.match(/(\\d+)\\s+(?:resultados|productos)/i)?.[1] || null")

    # ── interaccion con el rail compartido ──
    def click_marca(self, nombre):
        return self.nav.evaluar("""
          (() => {
            const l = [...document.querySelectorAll('app-filtros-catalogo .opcion-filtro')]
              .find(x => x.textContent.includes(%s));
            if (!l) return false;
            const c = l.querySelector('input[type="checkbox"]'); if (!c) return false;
            c.click(); return true; })()
        """ % json.dumps(nombre))

    def click_atributo(self, valor):
        return self.nav.evaluar("""
          (() => {
            const labels = [...document.querySelectorAll('app-filtros-catalogo .opcion-filtro')];
            const l = labels.find(x => x.querySelector('span') &&
                                       x.querySelector('span').textContent.trim() === %s);
            if (!l) return false;
            const c = l.querySelector('input[type="checkbox"]'); if (!c) return false;
            c.click(); return true; })()
        """ % json.dumps(valor))

    def set_orden(self, valor):
        return self.nav.evaluar("""
          (() => {
            const s = document.querySelector('#ordenSel'); if (!s) return false;
            const setter = Object.getOwnPropertyDescriptor(
              window.HTMLSelectElement.prototype, 'value').set;
            setter.call(s, %s);
            s.dispatchEvent(new Event('change', {bubbles: true})); return true; })()
        """ % json.dumps(valor))

    def siguiente(self):
        return self.nav.evaluar("""
          (() => { const b = [...document.querySelectorAll('.pagination .page-link')]
                     .find(x => /siguiente/i.test(x.textContent));
                   if (!b || b.closest('.page-item').classList.contains('disabled')) return false;
                   b.click(); return true; })()
        """)

    def abrir_ficha(self):
        return self.nav.evaluar("""
          (() => { const a = document.querySelector('a[href^="/producto/"]');
                   if (!a) return 0; const id = Number(a.getAttribute('href').split('/').pop());
                   a.click(); return id; })()
        """)

    # ── cuenta (para el carrito, que exige sesion) ──
    def entrar(self, correo, clave):
        """Inicia sesion por el MODAL de la aplicacion (no por la API).

        Se usa el administrador que ya siembra la pila: el carrito solo pide una
        sesion valida, cualquiera sirve, y el login (dos campos) es mas estable
        en headless que el registro completo.
        """
        self.nav.ir(FRONT + "/login")
        self.nav.esperar("document.querySelectorAll('input[formcontrolname]').length >= 2", 30)
        self.nav.evaluar(_poner_formulario({"email": correo, "password": clave}))
        self.nav.esperar("!!localStorage.getItem('sz_token')", 20)
        return bool(self.nav.evaluar("!!localStorage.getItem('sz_token')"))

    def agregar_al_carrito(self):
        """Anade el producto de la ficha y lo confirma en la PAGINA del carrito.

        Se comprueba en `/carrito` y no solo en el contador de la cabecera: es la
        verdad del backend (lee el carrito del usuario del token), no un numero
        de la interfaz que podria no haberse repintado.
        """
        auth = bool(self.nav.evaluar("!!localStorage.getItem('sz_token')"))
        pulsado = self.nav.evaluar("""
          (() => { const b = [...document.querySelectorAll('button')]
                     .find(x => /agregar a carrito/i.test(x.textContent));
                   if (!b || b.disabled) return false; b.click(); return true; })()
        """)
        self.nav.esperar("document.querySelector('.aviso-flotante')"
                         " || document.querySelector('.contador-carrito')"
                         " || location.pathname === '/login'", 15)
        aviso = self.nav.evaluar("document.querySelector('.aviso-flotante')?.textContent || ''")
        loc = self.nav.evaluar("location.pathname")
        self.nav.ir(FRONT + "/carrito")
        self.nav.esperar("location.pathname === '/carrito'", 20)
        time.sleep(3)
        items = int(self.nav.evaluar(
            "document.querySelectorAll('.nombre-item-link').length"))
        return {"auth": auth, "pulsado": pulsado, "aviso": aviso.strip(),
                "loc": loc, "items": items}


LISTO_BUSCAR = "location.pathname === '/buscar' && document.querySelector('app-filtros-catalogo')"
LISTO_CAT = ("location.pathname.indexOf('/categoria/') === 0 && "
             "document.querySelector('app-filtros-catalogo')")


def no_decreciente(xs):
    return all(xs[i] <= xs[i + 1] for i in range(len(xs) - 1))


def no_creciente(xs):
    return all(xs[i] >= xs[i + 1] for i in range(len(xs) - 1))


# ══════════════ Recorrido 1 · BUSQUEDA + carrito ══════════════

def recorrido_busqueda(total, acme):
    print("\n── Recorrido 1: Inicio -> buscar -> filtrar -> ordenar -> paginar -> ficha -> carrito")
    with Cliente("busqueda") as c:
        env = _env()
        anota("Inicia sesion (hace falta para el carrito)",
              c.entrar(env["ADMIN_EMAIL"], env["ADMIN_PASSWORD"]))

        c.ir("/buscar?q=" + TOKEN, LISTO_BUSCAR)
        c.espiar()
        base_ids = c.ids()
        base_total = c.total_texto()
        anota("La busqueda trae resultados del conjunto entero",
              len(base_ids) > 0 and base_total == str(total),
              "grid=%d total=%s (esperado %d)" % (len(base_ids), base_total, total))

        # Filtrar por una marca: el total baja al subconjunto y el filtro viaja.
        c.click_marca("E2E-Acme")
        c.nav.esperar("location.search.indexOf('marca=') >= 0", 20)
        time.sleep(LOTE)
        tras_marca = c.total_texto()
        pidio_marca = any("marcaId=" in u for u in c.urls())
        anota("Filtrar por marca recorta el total y viaja al servidor",
              tras_marca == str(acme) and "marca=" in c.query() and pidio_marca,
              "total=%s (marca=%d) url=%s" % (tras_marca, acme, c.query()))

        # Ordenar por precio ascendente: llega ya ordenado de la base.
        c.set_orden("precio-asc")
        c.nav.esperar("location.search.indexOf('orden=precio-asc') >= 0", 20)
        time.sleep(LOTE)
        precios = c.precios()
        pidio_orden = any("orden=PRECIO_ASC" in u for u in c.urls())
        anota("Ordenar por precio ascendente ordena en el servidor",
              len(precios) > 1 and no_decreciente(precios) and pidio_orden,
              "precios=%s" % precios)

        # Quitar la marca para tener paginas (>12) y paginar.
        c.click_marca("E2E-Acme")
        c.nav.esperar("location.search.indexOf('marca=') < 0", 20)
        time.sleep(LOTE)
        pag1 = c.ids()
        avanzo = c.siguiente()
        if avanzo:
            c.nav.esperar("location.search.indexOf('page=1') >= 0", 20)
            time.sleep(LOTE)
        pag2 = c.ids()
        anota("Paginar trae otra pagina, sin solape, y queda en la URL",
              avanzo and "page=1" in c.query() and pag2 and not (set(pag1) & set(pag2)),
              "pag1=%d pag2=%d url=%s" % (len(pag1), len(pag2), c.query()))

        # Abrir una ficha y anadir al carrito.
        pid = c.abrir_ficha()
        c.nav.esperar("location.pathname === '/producto/%d'" % pid, 30)
        c.nav.esperar("[...document.querySelectorAll('button')]"
                      ".some(b => /agregar a carrito/i.test(b.textContent))", 20)
        time.sleep(1)
        r = c.agregar_al_carrito()
        anota("Abrir la ficha y anadir al carrito lo deja en el carrito",
              pid > 0 and r["items"] >= 1,
              "producto=%d items=%d auth=%s aviso=%r loc=%s"
              % (pid, r["items"], r["auth"], r["aviso"], r["loc"]))


# ══════════════ Recorrido 2 · CATEGORIA ══════════════

def recorrido_categoria(total):
    print("\n── Recorrido 2: Categoria -> filtrar -> ordenar -> paginar -> ficha")
    with Cliente("categoria") as c:
        c.ir("/categoria/" + SLUG, LISTO_CAT)
        c.espiar()
        base_total = c.total_texto()
        grid = c.ids()
        anota("La categoria cuenta el conjunto entero y pagina a 12",
              base_total == str(total) and len(grid) == 12,
              "total=%s grid=%d (esperado %d)" % (base_total, len(grid), total))

        # Paginar en la categoria sin filtrar: hay segunda pagina (15 > 12).
        pag1 = c.ids()
        avanzo = c.siguiente()
        if avanzo:
            c.nav.esperar("location.search.indexOf('page=1') >= 0", 20)
            time.sleep(LOTE)
        pag2 = c.ids()
        anota("Paginar en la categoria trae la segunda pagina, sin solape",
              avanzo and pag2 and not (set(pag1) & set(pag2)),
              "pag1=%d pag2=%d" % (len(pag1), len(pag2)))

        # Volver, filtrar por atributo: el total baja al subconjunto.
        c.ir("/categoria/" + SLUG, LISTO_CAT)
        c.espiar()
        c.click_atributo("16")
        c.nav.esperar("location.search.indexOf('atr=') >= 0", 20)
        time.sleep(LOTE)
        tras_atr = c.total_texto()
        pidio_atr = any("atributo=" in u for u in c.urls())
        anota("Filtrar por atributo recorta el total y viaja al servidor",
              tras_atr is not None and int(tras_atr) < total and "atr=" in c.query() and pidio_atr,
              "total=%s url=%s" % (tras_atr, c.query()))

        # Ordenar descendente sobre el subconjunto filtrado.
        c.set_orden("precio-desc")
        c.nav.esperar("location.search.indexOf('orden=precio-desc') >= 0", 20)
        time.sleep(LOTE)
        precios = c.precios()
        anota("Ordenar el subconjunto por precio descendente lo ordena en el servidor",
              len(precios) >= 1 and no_creciente(precios), "precios=%s" % precios)

        # Abrir una ficha desde el resultado filtrado.
        pid = c.abrir_ficha()
        c.nav.esperar("location.pathname === '/producto/%d'" % pid, 30)
        anota("Abrir la ficha desde el resultado filtrado", pid > 0, "producto=%d" % pid)


def recorrer():
    # Perfiles de Chrome desde cero: el catalogo de la tienda sin filtros se
    # cachea en IndexedDB, y un perfil reutilizado de una corrida anterior
    # serviria el conteo viejo. Cada corrida empieza con el navegador en blanco.
    for nombre in ("busqueda", "categoria"):
        shutil.rmtree(os.path.join(AQUI, ".perfiles", nombre), ignore_errors=True)

    cat, total, acme = preparar()
    print("Catalogo E2E: categoria %d, %d productos, %d de E2E-Acme" % (cat, total, acme))
    recorrido_busqueda(total, acme)
    recorrido_categoria(total)

    fallos = [p for p in pasos if not p[1]]
    print("\nRESULTADO: %d comprobaciones, %d fallos" % (len(pasos), len(fallos)))
    for t, _, d in fallos:
        print("   FALLA  %s | %s" % (t, d))
    return not fallos


if __name__ == "__main__":
    sys.exit(0 if recorrer() else 1)
