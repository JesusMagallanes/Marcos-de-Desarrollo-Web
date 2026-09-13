"""Levanta la pila aislada, recorre los cuatro visitantes y la derriba.

    python docs/pruebas/descubrimiento/ejecutar.py            una pasada
    python docs/pruebas/descubrimiento/ejecutar.py --dos      dos pasadas desde cero

Cada pasada parte de una base VACIA: el proyecto Compose `smartzone-e2e` se
crea con volumenes propios y se destruye al terminar, con `down -v`. Por eso la
segunda pasada no puede depender de restos de la primera — que es exactamente
lo que se quiere comprobar con `--dos`.

Requisitos: Docker, Python 3 y Google Chrome instalado en la ruta habitual de
Windows. No se instala nada mas.
"""

import os
import subprocess
import sys
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", "..", ".."))
PROYECTO = "smartzone-e2e"
ENV = os.path.join(AQUI, ".env.e2e")


def compose(*args, **kw):
    return subprocess.run(
        ["docker", "compose", "-p", PROYECTO, "--profile", "default", "--env-file", ENV,
         *args], cwd=RAIZ, capture_output=True, text=True, **kw)


def comprobar_aislamiento():
    """Antes de arrancar nada: la pila que se va a levantar NO mira a Neon."""
    config = compose("config").stdout
    urls = [l.strip() for l in config.splitlines() if "DB_URL" in l]
    if not urls or any("neon" in u.lower() for u in urls) or not all(
            "postgres:5432/smartzone" in u for u in urls):
        print("ABORTADO: la configuracion no apunta a la base aislada:", urls)
        sys.exit(2)
    print("Datasource aislado:", urls[0].split(":", 1)[1].strip())


def levantar():
    print("Levantando la pila aislada desde cero...")
    r = compose("up", "-d", "--build", timeout=1800)
    if r.returncode != 0:
        print(r.stderr[-1500:])
        sys.exit(2)
    for _ in range(60):
        estado = compose("ps", "--format", "{{.Name}} {{.Status}}").stdout
        sanos = estado.count("(healthy)")
        if sanos >= 5 and "frontend" in estado:
            time.sleep(5)
            print(estado.strip())
            return
        time.sleep(3)
    print("ABORTADO: la pila no llego a estar sana")
    print(estado)
    sys.exit(2)


def derribar():
    compose("down", "-v", timeout=600)


def pasada(numero):
    print("\n══════════ PASADA %d ══════════" % numero)
    comprobar_aislamiento()
    levantar()
    try:
        # `-u` para que la salida del hijo salga linea a linea: bajo
        # redireccion a fichero, sin esto Python la retiene en un buffer de
        # bloque y el resultado real se pierde.
        r = subprocess.run([sys.executable, "-u", os.path.join(AQUI, "visitantes.py")],
                           cwd=RAIZ,
                           env={**os.environ, "PYTHONIOENCODING": "utf-8",
                                "PYTHONUNBUFFERED": "1"})
        return r.returncode == 0
    finally:
        derribar()


if __name__ == "__main__":
    pasadas = 2 if "--dos" in sys.argv else 1
    resultados = [pasada(i + 1) for i in range(pasadas)]
    print("\nPASADAS:", " ".join("PASS" if ok else "FAIL" for ok in resultados))
    sys.exit(0 if all(resultados) else 1)
