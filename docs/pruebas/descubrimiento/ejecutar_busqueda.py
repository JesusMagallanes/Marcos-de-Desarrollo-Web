"""Levanta la pila aislada, corre los recorridos de busqueda/categoria y la derriba.

    python docs/pruebas/descubrimiento/ejecutar_busqueda.py

Mismo aislamiento que `ejecutar.py`: proyecto Compose `smartzone-e2e`, volumenes
propios, base VACIA y `down -v` al terminar. Nunca Neon. Reusa la orquestacion
de `ejecutar.py` (comprobacion de aislamiento, levantado y derribo) para no
duplicarla; lo unico propio es que aqui se ejecuta `recorridos_busqueda.py`.
"""

import os
import subprocess
import sys

# UTF-8 pase lo que pase: el banner lleva caracteres de recuadro y bajo
# redireccion a fichero la consola de Windows (cp1252) reventaria.
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)
from ejecutar import comprobar_aislamiento, levantar, derribar, RAIZ


def pasada():
    print("\n══════════ RECORRIDOS DE BUSQUEDA Y CATEGORIA ══════════")
    comprobar_aislamiento()
    levantar()
    try:
        r = subprocess.run(
            [sys.executable, "-u", os.path.join(AQUI, "recorridos_busqueda.py")],
            cwd=RAIZ,
            env={**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONUNBUFFERED": "1"})
        return r.returncode == 0
    finally:
        derribar()


if __name__ == "__main__":
    ok = pasada()
    print("\nRECORRIDOS:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)
