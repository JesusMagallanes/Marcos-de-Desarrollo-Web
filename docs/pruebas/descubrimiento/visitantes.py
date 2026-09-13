"""Los cuatro visitantes de la fase J, contra la pila completa y base aislada.

Cada visitante abre SU PROPIO Chromium, con su propio perfil de datos y su
propio puerto de depuracion, y lo cierra al terminar por el protocolo. Nunca se
mata Chrome por nombre: el navegador personal de quien ejecuta esto no se toca.

Lo que se comprueba son INVARIANTES del pipeline, no listas exactas de
productos: el algoritmo es determinista pero depende del estado del catalogo, y
una prueba que fijara el orden se romperia con cualquier alta de producto sin
que nada se hubiera roto de verdad.
"""

import json
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp import Navegador

AQUI = os.path.dirname(os.path.abspath(__file__))
PROYECTO = "smartzone-e2e"
FRONT = "http://localhost:14200"
PG = ["docker", "exec", PROYECTO + "-postgres-1",
      "psql", "-U", "smartzone", "-d", "smartzone", "-t", "-A", "-F", "|", "-c"]

CLAVE_SUJETO = "sz.descubrimiento.sujeto"
CLAVE_FIRMA = "sz.descubrimiento.firma"
HAY_CARRUSEL = ("document.querySelectorAll("
                "'section.carrusel-desc a[href*=\"/producto/\"]').length > 0")
LOTE = 9   # el cliente manda impresiones cada cinco segundos; se espera holgado

pasos = []
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", re.I)


# ══════════════ Infraestructura minima ══════════════

def sql(consulta):
    r = subprocess.run(PG + [consulta], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip()[:300])
    return [l.split("|") for l in r.stdout.strip().splitlines() if l]


def uno(consulta):
    filas = sql(consulta)
    return filas[0][0] if filas and filas[0] else None


def anota(visitante, titulo, ok, detalle=""):
    pasos.append((visitante, titulo, ok, detalle))
    print("  %s  | %s | %s%s" % ("OK " if ok else "FALLA", visitante, titulo,
                                 (" | " + detalle) if detalle else ""))


class Visitante:
    """Un navegador propio, limpio, que se cierra solo."""

    _puerto = 9240

    def __init__(self, nombre):
        Visitante._puerto += 1
        self.nombre = nombre
        self.nav = Navegador(perfil=os.path.join(AQUI, ".perfiles", nombre),
                             puerto=Visitante._puerto)
        self.nav.ir(FRONT + "/")
        self.nav.esperar(HAY_CARRUSEL, 60)
        self.nav.evaluar("localStorage.clear()")

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.nav.cerrar()

    # ── navegacion ──
    def home(self, esperar=LOTE):
        self.nav.ir(FRONT + "/")
        self.nav.esperar(HAY_CARRUSEL, 60)
        self.nav.evaluar("window.scrollTo(0, 0)")
        time.sleep(esperar)
        return self.tarjetas()

    def ficha(self, producto, esperar=LOTE):
        self.nav.ir(FRONT + "/producto/%d" % producto)
        self.nav.esperar("location.pathname.indexOf('/producto/') === 0", 30)
        self.nav.esperar(HAY_CARRUSEL, 60)
        time.sleep(esperar)
        return self.tarjetas()

    def buscar(self, termino, esperar=LOTE):
        self.nav.ir(FRONT + "/buscar?q=" + termino)
        time.sleep(esperar)

    def ficha_spa(self, producto, esperar=LOTE):
        """Entra a la ficha pulsando el enlace, sin recargar la aplicacion.

        Importa para C: cada carga completa estrena un identificador de sesion
        en el cliente, asi que navegar por URL rompe la sesion. Una persona
        pulsa enlaces; esto hace lo mismo.
        """
        pulsado = self.nav.evaluar("""
          (() => { const a = document.querySelector('a[href="/producto/%d"]');
                   if (!a) return false; a.click(); return true; })()
        """ % producto)
        if not pulsado:
            return self.ficha(producto, esperar)
        self.nav.esperar("location.pathname === '/producto/%d'" % producto, 30)
        self.nav.esperar(HAY_CARRUSEL, 60)
        time.sleep(esperar)
        return self.tarjetas()

    def buscar_spa(self, termino, esperar=LOTE):
        """Busca desde la cabecera de la aplicacion, sin recargar."""
        hecho = self.nav.evaluar("""
          (() => {
            const setter = Object.getOwnPropertyDescriptor(
              window.HTMLInputElement.prototype, 'value').set;
            const input = document.querySelector('input[type="search"], input[name*="busc" i],'
              + ' input[placeholder*="Busc" i], form input[type="text"]');
            if (!input) return false;
            setter.call(input, %s);
            input.dispatchEvent(new Event('input', {bubbles: true}));
            const form = input.closest('form');
            if (form) { form.requestSubmit(); return true; }
            input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}));
            return true;
          })()
        """ % json.dumps(termino))
        if not hecho:
            return self.buscar(termino, esperar)
        time.sleep(esperar)
        return True

    def volver_al_home_spa(self, esperar=LOTE):
        pulsado = self.nav.evaluar("""
          (() => { const a = document.querySelector('a[href="/"], a.navbar-brand');
                   if (!a) return false; a.click(); return true; })()
        """)
        if not pulsado:
            return self.home(esperar)
        self.nav.esperar("location.pathname === '/'", 30)
        self.nav.esperar(HAY_CARRUSEL, 60)
        self.nav.evaluar("window.scrollTo(0, 0)")
        time.sleep(esperar)
        return self.tarjetas()

    def espiar_urls(self):
        """Instala un espia de fetch/XHR/sendBeacon en la pagina actual."""
        self.nav.evaluar("""
          if (!window.__urls) {
            window.__urls = [];
            const f = window.fetch;
            window.fetch = function (...a) { try { window.__urls.push(String(a[0])); } catch (e) {}
                                             return f.apply(this, a); };
            const o = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function (m, u) {
              try { window.__urls.push(String(u)); } catch (e) {} return o.apply(this, arguments); };
            const b = navigator.sendBeacon.bind(navigator);
            navigator.sendBeacon = function (u, d) {
              try { window.__urls.push(String(u)); } catch (e) {} return b(u, d); };
          }
        """)

    def urls(self):
        return json.loads(self.nav.evaluar("JSON.stringify(window.__urls || [])"))

    def tarjetas(self):
        return json.loads(self.nav.evaluar("""
          (() => { const v = [];
            document.querySelectorAll('section.carrusel-desc a[href*="/producto/"]')
              .forEach(a => { const id = Number(a.getAttribute('href').split('/').pop());
                              if (id && !v.includes(id)) v.push(id); });
            return JSON.stringify(v); })()
        """))

    def modulos(self):
        return json.loads(self.nav.evaluar("""
          JSON.stringify([...document.querySelectorAll('section.carrusel-desc h2')]
            .map(h => h.textContent.trim()))
        """))

    def no_me_interesa(self, producto):
        """Por el menu de la tarjeta, como lo haria una persona."""
        abierto = self._abrir_menu(producto)
        if abierto != 'menu abierto':
            return abierto
        # El menu lo pinta Angular tras el clic; hay que darle un instante.
        time.sleep(1)
        return self.nav.evaluar("""
          (() => {
            const boton = [...document.querySelectorAll('button')]
              .find(b => b.textContent.trim() === 'No me interesa');
            if (!boton) return 'sin opcion';
            boton.click();
            return 'pulsado';
          })()
        """)

    def _abrir_menu(self, producto):
        return self.nav.evaluar("""
          (() => {
            const a = document.querySelector('section.carrusel-desc a[href="/producto/%d"]');
            if (!a) return 'sin tarjeta';
            let tarjeta = a.parentElement, opciones = null;
            for (let i = 0; i < 8 && tarjeta && !opciones; i++) {
              opciones = tarjeta.querySelector('button[aria-label^="Opciones"]');
              if (!opciones) tarjeta = tarjeta.parentElement;
            }
            if (!opciones) return 'sin menu';
            opciones.click();
            return 'menu abierto';
          })()
        """ % producto)

    # ── identidad ──
    def sujeto(self):
        return self.nav.evaluar("localStorage.getItem('%s')" % CLAVE_SUJETO)

    def firma(self):
        return self.nav.evaluar("localStorage.getItem('%s')" % CLAVE_FIRMA)

    def registrarse_y_entrar(self, correo, clave):
        """Registro y login por el MODAL de la aplicacion, no por la API."""
        self.nav.ir(FRONT + "/login?modo=registro")
        self.nav.esperar("document.querySelectorAll('input[formcontrolname]').length >= 5", 30)
        self.nav.evaluar(_poner_formulario({
            "name": "Visitante", "lastname": "Cuatro", "emailAddress": correo,
            "password": clave, "address": "Av. Grau 123, Ica", "phoneNumber": "987654321",
        }))
        time.sleep(8)
        if not self.nav.evaluar("!!localStorage.getItem('sz_token')"):
            # Algunas versiones no inician sesion al registrar: se entra a mano.
            self.nav.ir(FRONT + "/login")
            self.nav.esperar("document.querySelectorAll('input[formcontrolname]').length >= 2", 30)
            self.nav.evaluar(_poner_formulario({"email": correo, "password": clave}))
            time.sleep(8)
        return bool(self.nav.evaluar("!!localStorage.getItem('sz_token')"))

    def cerrar_sesion(self):
        """Por el boton de la aplicacion, que es lo que suelta el sujeto."""
        self.nav.ir(FRONT + "/perfil/cuenta")
        self.nav.esperar("document.body.innerText.length > 100", 30)
        time.sleep(2)
        pulsado = self.nav.evaluar("""
          (() => {
            const b = [...document.querySelectorAll('button')]
              .find(x => /cerrar sesi/i.test(x.textContent));
            if (!b) return false;
            b.click(); return true;
          })()
        """)
        time.sleep(4)
        return pulsado


def _poner_formulario(campos):
    """Rellena controles reactivos de Angular disparando los eventos que escucha."""
    return """
      (() => {
        const setter = Object.getOwnPropertyDescriptor(
          window.HTMLInputElement.prototype, 'value').set;
        const poner = (nombre, valor) => {
          const el = document.querySelector('input[formcontrolname="' + nombre + '"]');
          if (!el) return false;
          setter.call(el, valor);
          el.dispatchEvent(new Event('input', {bubbles: true}));
          el.dispatchEvent(new Event('blur', {bubbles: true}));
          return true;
        };
        const campos = %s;
        let ok = true, form = null;
        for (const [k, v] of Object.entries(campos)) {
          ok = poner(k, v) && ok;
          const el = document.querySelector('input[formcontrolname="' + k + '"]');
          if (el) form = el.closest('form');
        }
        if (form) form.requestSubmit();
        return ok && !!form;
      })()
    """ % json.dumps(campos)


# ══════════════ Datos de apoyo: solo dentro de la base aislada ══════════════

def preparar_catalogo():
    """Una categoria propia con productos elegibles y uno agotado.

    Todo dentro de la base del contenedor. No toca nada externo.
    """
    sql("""
        INSERT INTO catalogo.categoria (name, slug, description)
        VALUES ('e2e-monitores', 'e2e-monitores', 'E2E') ON CONFLICT (slug) DO NOTHING;
    """)
    cat = uno("SELECT id FROM catalogo.categoria WHERE slug = 'e2e-monitores'")
    for i in range(1, 7):
        sql("""
            INSERT INTO catalogo.producto
                   (name, description, precio, stock, categoria_id, estado_moderacion)
            SELECT 'E2E monitor %d', 'E2E', 100.00 + %d, 10, %s, 'APROBADO'
             WHERE NOT EXISTS (SELECT 1 FROM catalogo.producto WHERE name = 'E2E monitor %d');
        """ % (i, i, cat, i))
    sql("""
        INSERT INTO catalogo.producto
               (name, description, precio, stock, categoria_id, estado_moderacion)
        SELECT 'E2E monitor agotado', 'E2E', 100.00, 0, %s, 'APROBADO'
         WHERE NOT EXISTS (SELECT 1 FROM catalogo.producto WHERE name = 'E2E monitor agotado');
    """ % cat)
    ids = [int(f[0]) for f in sql(
        "SELECT id FROM catalogo.producto WHERE name LIKE 'E2E monitor %%' AND stock > 0 ORDER BY id")]
    agotado = int(uno("SELECT id FROM catalogo.producto WHERE name = 'E2E monitor agotado'"))

    # Una segunda categoria, para el caso que justifica la intencion de sesion.
    sql("""
        INSERT INTO catalogo.categoria (name, slug, description)
        VALUES ('e2e-impresoras', 'e2e-impresoras', 'E2E') ON CONFLICT (slug) DO NOTHING;
    """)
    otra = int(uno("SELECT id FROM catalogo.categoria WHERE slug = 'e2e-impresoras'"))
    for i in range(1, 4):
        sql("""
            INSERT INTO catalogo.producto
                   (name, description, precio, stock, categoria_id, estado_moderacion)
            SELECT 'E2E impresora %d', 'E2E', 200.00 + %d, 10, %s, 'APROBADO'
             WHERE NOT EXISTS (SELECT 1 FROM catalogo.producto WHERE name = 'E2E impresora %d');
        """ % (i, i, otra, i))
    return int(cat), ids, agotado, otra


def sin_errores_ni_identificadores(visitante, sujetos):
    """El log del backend: sin errores y sin ningun identificador de sujeto."""
    log = subprocess.run(["docker", "logs", "--since", "15m", PROYECTO + "-catalogo-1"],
                         capture_output=True, text=True)
    texto = log.stdout + log.stderr
    graves = [l for l in texto.splitlines() if "ERROR" in l and "RLS INACTIVO" not in l]
    filtrados = [s for s in sujetos if s and s in texto]
    anota(visitante, "El backend no registra errores ni identificadores de sujeto",
          not graves and not filtrados,
          "%d errores, %d sujetos en el log" % (len(graves), len(filtrados)))


# ══════════════ VISITANTE 1 · anonimo, arranque en frio ══════════════

def visitante_1(agotado):
    V = "V1 frio"
    with Visitante("v1-frio") as v:
        tarjetas = v.home(esperar=0)
        v.espiar_urls()
        time.sleep(LOTE)
        s1 = v.sujeto()
        anota(V, "Recibe un Home sin perfil ni rastro", len(tarjetas) > 0 and bool(s1),
              "%d tarjetas, modulos: %s" % (len(tarjetas), ", ".join(v.modulos())))
        anota(V, "El servidor le entrego identificador y firma", bool(v.firma()))
        anota(V, "Sin perfil historico en la base",
              uno("SELECT COUNT(*) FROM catalogo.perfil_faceta WHERE sujeto_id = '%s'" % s1) == "0")
        anota(V, "Su Home queda anotado como arranque en frio",
              uno("SELECT COUNT(*) FROM catalogo.recomendacion_servida"
                  " WHERE sujeto_id = '%s' AND con_perfil = true" % s1) == "0")
        anota(V, "Un producto agotado nunca es candidato", agotado not in tarjetas)

        vistas = int(uno("SELECT COUNT(*) FROM catalogo.impresion WHERE sujeto_id = '%s'" % s1))
        anota(V, "Las impresiones llegan por el flujo real del cliente", vistas > 0,
              "%d impresiones" % vistas)
        anota(V, "Todas con una recomendacion servida detras",
              uno("""SELECT COUNT(*) FROM catalogo.impresion i
                      WHERE i.sujeto_id = '%s' AND NOT EXISTS (
                        SELECT 1 FROM catalogo.recomendacion_servida r
                         WHERE r.sujeto_id = i.sujeto_id AND r.item_id = i.item_id
                           AND r.modulo = i.modulo)""" % s1) == "0")

        elegido = tarjetas[0]
        relacionados = v.ficha(elegido)
        anota(V, "Entra a una ficha y recibe relacionados", len(relacionados) > 0,
              "%d relacionados" % len(relacionados))
        anota(V, "El clic queda como evento del propio sujeto",
              uno("SELECT COUNT(*) FROM catalogo.evento_interaccion"
                  " WHERE sujeto_id = '%s' AND item_id = %d" % (s1, elegido)) != "0")

        de_nuevo = v.home()
        anota(V, "Vuelve al Home y sigue funcionando con el mismo sujeto",
              len(de_nuevo) > 0 and v.sujeto() == s1)
        sin_errores_ni_identificadores(V, [s1])
        return s1


# ══════════════ VISITANTE 2 · anonimo con intencion de sesion ══════════════

def visitante_2(cat, ids, otra):
    V = "V2 sesion"
    with Visitante("v2-sesion") as v:
        v.home(esperar=3)
        s2 = v.sujeto()
        sin_perfil_al_llegar = uno(
            "SELECT COUNT(*) FROM catalogo.perfil_faceta WHERE sujeto_id = '%s'" % s2) == "0"
        # Dos fichas de la misma categoria y una busqueda, SIN recargar la
        # aplicacion: asi la sesion del cliente es una sola, como en C.
        v.ficha(ids[0], esperar=3)
        v.ficha_spa(ids[1], esperar=3)
        v.buscar_spa("monitor", esperar=LOTE)

        eventos = int(uno("SELECT COUNT(*) FROM catalogo.evento_interaccion"
                          " WHERE sujeto_id = '%s'" % s2))
        anota(V, "La navegacion real dejo eventos de sesion", eventos >= 3,
              "%d eventos" % eventos)
        anota(V, "Los eventos comparten una sesion viva",
              uno("SELECT COUNT(DISTINCT sesion_id) FROM catalogo.evento_interaccion"
                  " WHERE sujeto_id = '%s'" % s2) == "1")

        tarjetas = v.volver_al_home_spa()
        modulos = v.modulos()
        personal = any("intereses" in m.lower() or "explorando" in m.lower() for m in modulos)
        anota(V, "Quien llego sin perfil recibe el modulo personal tras su sesion",
              sin_perfil_al_llegar and personal,
              "modulos: %s" % ", ".join(modulos))
        # Con un sujeto nuevo, sesion y perfil senalan la misma categoria y C
        # entrega la atribucion al perfil A PROPOSITO: los candidatos de sesion
        # se piden excluyendo los del perfil para no perder de que senal vino
        # cada uno. Por eso aqui no se exige SESSION_INTENT: se exige en el
        # contraste de abajo, que es el caso para el que C existe.
        anota(V, "La sesion no se persiste como estado propio: solo se deduce de eventos",
              uno("SELECT COUNT(*) FROM information_schema.tables"
                  " WHERE table_schema = 'catalogo' AND table_name LIKE '%%sesion%%'") == "0")

        # NOT_INTERESTED por el menu de la tarjeta, y no puede volver.
        objetivo = tarjetas[0]
        cat_objetivo = uno("SELECT categoria_id FROM catalogo.producto WHERE id = %d" % objetivo)
        score_antes = float(uno("""SELECT COALESCE(MAX(f.score), 0) FROM catalogo.perfil_faceta f
                                    JOIN catalogo.categoria c ON c.name = f.faceta
                                   WHERE f.sujeto_id = '%s' AND f.tipo_faceta = 'CATEGORIA'
                                     AND c.id = %s""" % (s2, cat_objetivo)))
        pulsado = v.no_me_interesa(objetivo)
        time.sleep(4)
        descartado = uno("SELECT COUNT(*) FROM catalogo.item_descartado"
                         " WHERE sujeto_id = '%s' AND item_id = %d" % (s2, objetivo))
        anota(V, "«No me interesa» desde la tarjeta deja el descarte", descartado == "1",
              "%s" % pulsado)
        score_despues = float(uno("""SELECT COALESCE(MAX(f.score), 0) FROM catalogo.perfil_faceta f
                                      JOIN catalogo.categoria c ON c.name = f.faceta
                                     WHERE f.sujeto_id = '%s' AND f.tipo_faceta = 'CATEGORIA'
                                       AND c.id = %s""" % (s2, cat_objetivo)))
        anota(V, "El descarte no es una senal positiva: el interes por esa categoria no sube",
              score_despues <= score_antes,
              "score %.2f -> %.2f" % (score_antes, score_despues))
        tras = v.home(esperar=3)
        anota(V, "Y el producto descartado no vuelve al Home", objetivo not in tras)
        sin_errores_ni_identificadores(V, [s2])

    # ── El caso para el que C existe: «le gustan las impresoras, hoy mira monitores» ──
    with Visitante("v2-contraste") as w:
        w.home(esperar=3)
        s2b = w.sujeto()
        # Un perfil PREPARADO en otra categoria, para ESTE sujeto y en la base
        # aislada. La sesion que viene ahora lo contradice, y eso es lo que C
        # tiene que saber escuchar sin borrar el perfil.
        nombre_otra = uno("SELECT name FROM catalogo.categoria WHERE id = %d" % otra)
        sql("""INSERT INTO catalogo.perfil_faceta
                      (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
               VALUES ('%s', 'CATEGORIA', '%s', 90.0, 40, now())
               ON CONFLICT DO NOTHING""" % (s2b, nombre_otra))
        w.home(esperar=3)
        # Espera completa (>= intervalo de lote del cliente): los eventos de
        # estas dos fichas tienen que estar PERSISTIDOS antes de medir el Home,
        # o la sesion viva no los ve todavia. Con 3s se corria contra el batch de
        # 5s y SESSION_INTENT salia 0 por carrera, no por el codigo.
        w.ficha_spa(ids[0])
        w.ficha_spa(ids[1])
        tarjetas = w.volver_al_home_spa()

        intencion = int(uno("SELECT COUNT(*) FROM catalogo.recomendacion_servida"
                            " WHERE sujeto_id = '%s' AND razon = 'SESSION_INTENT'" % s2b))
        monitores_en_home = [t for t in tarjetas if t in ids]
        # Desde la fase K la atribucion de sesion se decide al anotar, asi que
        # ahora SESSION_INTENT SI aparece desde el flujo real: la categoria de
        # esta visita llega al Home pese a un perfil que dice lo contrario, y
        # queda anotada como intencion de sesion. Antes de K esto era 0.
        anota(V, "La intencion de esta visita produce SESSION_INTENT real",
              len(monitores_en_home) > 0 and intencion > 0,
              "%d monitores en el Home; %d anotados como SESSION_INTENT"
              % (len(monitores_en_home), intencion))
        anota(V, "Y el perfil historico no se borra por una visita",
              uno("""SELECT COUNT(*) FROM catalogo.perfil_faceta
                      WHERE sujeto_id = '%s' AND faceta = '%s' AND score > 0"""
                  % (s2b, nombre_otra)) == "1")
        sin_errores_ni_identificadores(V, [s2b])
    return s2


# ══════════════ VISITANTE 3 · con perfil e historial ══════════════

def visitante_3(cat, ids):
    V = "V3 perfil"
    with Visitante("v3-perfil") as v:
        v.home(esperar=3)
        s3 = v.sujeto()
        # El historial se PREPARA para este sujeto —el que el servidor firmo— en
        # la base aislada. No se fabrica ninguna firma ni se toca otro sujeto.
        nombre_cat = uno("SELECT name FROM catalogo.categoria WHERE id = %d" % cat)
        sql("""INSERT INTO catalogo.perfil_faceta
                      (sujeto_id, tipo_faceta, faceta, score, eventos, actualizado_en)
               VALUES ('%s', 'CATEGORIA', '%s', 80.0, 30, now())
               ON CONFLICT DO NOTHING""" % (s3, nombre_cat))
        for p in ids[:3]:
            sql("""INSERT INTO catalogo.evento_interaccion
                          (sujeto_id, tipo, item_tipo, item_id, categoria_id, ocurrido_en)
                   VALUES ('%s', 'ITEM_VIEW_DEEP', 'PRODUCTO', %d, %d, now() - interval '2 days')"""
                % (s3, p, cat))

        tarjetas = v.home()
        modulos = v.modulos()
        anota(V, "Con perfil, el Home sirve el modulo personal",
              any("intereses" in m.lower() for m in modulos), ", ".join(modulos))
        anota(V, "Y queda anotado como Home CON perfil",
              uno("SELECT COUNT(*) FROM catalogo.recomendacion_servida"
                  " WHERE sujeto_id = '%s' AND con_perfil = true" % s3) != "0")
        anota(V, "Las razones anotadas son del conjunto cerrado y no exponen a nadie",
              uno("""SELECT COUNT(*) FROM catalogo.recomendacion_servida
                      WHERE sujeto_id = '%s' AND razon NOT IN
                      ('PERSONAL_INTEREST','CONTENT_SIMILAR','CO_VIEWED','SIMILAR_SUBJECT',
                       'LOCAL_TREND','EXPLORATION','POPULAR','NEW_ARRIVAL','SESSION_INTENT')"""
                  % s3) == "0")
        anota(V, "Lo servido a este sujeto no incluye recomendaciones de otro",
              uno("SELECT COUNT(DISTINCT sujeto_id) FROM catalogo.recomendacion_servida"
                  " WHERE sujeto_id = '%s'" % s3) == "1")

        relacionados = v.ficha(ids[0])
        anota(V, "La ficha da relacionados y no se recomienda a si misma",
              len(relacionados) > 0 and ids[0] not in relacionados)

        # Cooldown: repetir el Home hasta que la cabecera pierda su sitio.
        cabecera = tarjetas[0]
        historial = []
        imps = 0
        for _ in range(8):
            actual = v.home(esperar=LOTE)
            imps = int(uno("SELECT COUNT(*) FROM catalogo.impresion WHERE sujeto_id = '%s'"
                           " AND item_id = %d" % (s3, cabecera)))
            historial.append(actual.index(cabecera) + 1 if cabecera in actual else 0)
            if historial[-1] == 0:
                break
        # El corte de D esta en seis impresiones sin clic. Por debajo, el
        # producto se descuenta pero puede seguir ganando su puesto; al llegar,
        # sale de ese carrusel. Con un solo sujeto el freno global del ranker
        # no actua —el piso de sujetos de H lo impide, y es lo acordado—, asi
        # que lo unico que puede retirarlo es el cooldown. Eso es lo que se mide.
        anota(V, "Alcanzado el corte de exposicion, el producto sale (cooldown D)",
              imps >= 6 and historial[-1] == 0,
              "puestos por vuelta: %s; %d impresiones del producto" % (historial, imps))

        # NOT_INTERESTED es exclusion dura, tambien con perfil.
        tarjetas = v.home(esperar=3)
        objetivo = tarjetas[0]
        v.no_me_interesa(objetivo)
        time.sleep(4)
        tras = v.home(esperar=3)
        anota(V, "El descartado no reaparece aunque el perfil lo favorezca",
              objetivo not in tras)
        anota(V, "Todo lo servido sigue siendo elegible",
              uno("""SELECT COUNT(*) FROM catalogo.recomendacion_servida r
                      JOIN catalogo.producto p ON p.id = r.item_id
                     WHERE r.sujeto_id = '%s'
                       AND (p.estado_moderacion <> 'APROBADO' OR p.stock <= 0)""" % s3) == "0")
        sin_errores_ni_identificadores(V, [s3])
        return s3


# ══════════════ VISITANTE 4 · autenticado con fusion ══════════════

def visitante_4(ids):
    V = "V4 fusion"
    correo = "e2e.v4.%d@smartzone.test" % int(time.time())
    clave = "E2eVisitante!2026"
    with Visitante("v4-fusion") as v:
        v.home(esperar=3)
        anonimo = v.sujeto()
        firma_anonima = v.firma()
        v.ficha(ids[0])
        v.ficha(ids[1])
        antes = int(uno("SELECT COUNT(*) FROM catalogo.evento_interaccion"
                        " WHERE sujeto_id = '%s'" % anonimo))
        anota(V, "El anonimo genera senales propias", antes >= 2, "%d eventos" % antes)

        entro = v.registrarse_y_entrar(correo, clave)
        anota(V, "Se registra e inicia sesion por el modal de la aplicacion", entro)

        v.home(esperar=LOTE)
        cuenta = v.sujeto()
        anota(V, "Al entrar, el servidor le da el sujeto de la cuenta", cuenta and cuenta != anonimo)
        anota(V, "El sujeto anonimo queda marcado como fusionado",
              uno("SELECT fusionado_en IS NOT NULL FROM catalogo.sujeto WHERE id = '%s'"
                  % anonimo) == "t")
        movidos = int(uno("SELECT COUNT(*) FROM catalogo.evento_interaccion"
                          " WHERE sujeto_id = '%s'" % cuenta))
        anota(V, "Su historico anonimo queda asociado a la cuenta", movidos >= antes,
              "%d eventos bajo la cuenta" % movidos)
        anota(V, "Y no queda nada huerfano bajo el anonimo",
              uno("SELECT COUNT(*) FROM catalogo.evento_interaccion"
                  " WHERE sujeto_id = '%s'" % anonimo) == "0")

        # El anonimo ya no sirve como credencial, ni siquiera con su firma vieja.
        reutilizado = v.nav.evaluar("""
          (async () => {
            const r = await fetch('/api/descubrimiento/home', {headers: {
              'X-Sujeto': '%s', 'X-Sujeto-Firma': '%s'}});
            return (await r.json()).sujetoId;
          })()
        """ % (anonimo, firma_anonima))
        anota(V, "El anonimo fusionado ya no puede usarse como identidad independiente",
              reutilizado and reutilizado != anonimo,
              "el servidor responde con otro sujeto")

        salio = v.cerrar_sesion()
        siguiente = v.home(esperar=3)
        nuevo = v.sujeto()
        anota(V, "Cierra sesion por la aplicacion y el siguiente visitante estrena sujeto",
              salio and nuevo and nuevo not in (anonimo, cuenta) and len(siguiente) > 0)
        anota(V, "Sin contaminacion: el nuevo no hereda perfil ni eventos",
              uno("SELECT COUNT(*) FROM catalogo.perfil_faceta WHERE sujeto_id = '%s'" % nuevo) == "0"
              and uno("SELECT COUNT(*) FROM catalogo.evento_interaccion WHERE sujeto_id = '%s'"
                      % nuevo) == "0")
        # Ultima pagina con espia: ninguna peticion puede llevar el sujeto.
        v.home(esperar=0)
        v.espiar_urls()
        time.sleep(LOTE)
        urls = v.urls()
        anota(V, "El sujeto no viaja en ninguna URL",
              len(urls) > 0 and not any(x and x in u for u in urls for x in (anonimo, cuenta, nuevo)),
              "%d peticiones observadas" % len(urls))
        sin_errores_ni_identificadores(V, [anonimo, cuenta, nuevo])
        return anonimo, cuenta, nuevo


# ══════════════ Aislamiento entre los cuatro ══════════════

def aislamiento(sujetos):
    V = "Aislamiento"
    distintos = len(set(s for s in sujetos if s)) == len([s for s in sujetos if s])
    anota(V, "Los sujetos de los cuatro recorridos son todos distintos", distintos,
          "%d sujetos" % len(sujetos))
    cruzadas = uno("""SELECT COUNT(*) FROM catalogo.recomendacion_servida r
                       WHERE r.sujeto_id IN (%s)
                         AND NOT EXISTS (SELECT 1 FROM catalogo.sujeto s WHERE s.id = r.sujeto_id)"""
                   % ",".join("'%s'" % s for s in sujetos if s))
    anota(V, "Ninguna recomendacion servida apunta a un sujeto inexistente", cruzadas == "0")


# ══════════════ Metricas, con credenciales de administrador ══════════════

def metricas_sin_identificadores(sujetos):
    V = "Metricas"
    env = _env()
    login = subprocess.run(
        ["docker", "exec", PROYECTO + "-catalogo-1", "wget", "-qO-",
         "--header=Content-Type: application/json", "--post-data",
         json.dumps({"email": env["ADMIN_EMAIL"], "password": env["ADMIN_PASSWORD"]}),
         "http://usuarios:8082/api/auth/login"], capture_output=True, text=True).stdout
    try:
        token = json.loads(login)["accessToken"]
    except Exception:
        anota(V, "Prometheus accesible con rol de administrador", False, "sin token")
        return
    salida = subprocess.run(
        ["docker", "exec", PROYECTO + "-catalogo-1", "wget", "-qO-",
         "--header=Authorization: Bearer " + token,
         "http://localhost:8081/actuator/prometheus"], capture_output=True, text=True).stdout
    lineas = [l for l in salida.splitlines() if l.startswith("smartzone_descubrimiento")]
    anota(V, "Prometheus expone las metricas de descubrimiento", len(lineas) > 0,
          "%d series" % len(lineas))
    texto = "\n".join(lineas)
    anota(V, "Sin UUID ni sujeto en ninguna etiqueta",
          not UUID_RE.search(texto) and not any(s in texto for s in sujetos if s))


def _env():
    env = {}
    with open(os.path.join(AQUI, ".env.e2e"), encoding="utf-8") as f:
        for linea in f:
            if "=" in linea and not linea.startswith("#"):
                k, v = linea.strip().split("=", 1)
                env[k] = v
    return env


# ══════════════ Orquestacion de los cuatro ══════════════

def recorrer():
    cat, ids, agotado, otra = preparar_catalogo()
    s1 = visitante_1(agotado)
    s2 = visitante_2(cat, ids, otra)
    s3 = visitante_3(cat, ids)
    a4, c4, n4 = visitante_4(ids)
    aislamiento([s1, s2, s3, a4, c4, n4])
    metricas_sin_identificadores([s1, s2, s3, a4, c4, n4])

    fallos = [p for p in pasos if not p[2]]
    print()
    print("RESULTADO: %d comprobaciones, %d fallos" % (len(pasos), len(fallos)))
    for v, t, _, d in fallos:
        print("   FALLA  %s | %s | %s" % (v, t, d))
    return not fallos


if __name__ == "__main__":
    sys.exit(0 if recorrer() else 1)
